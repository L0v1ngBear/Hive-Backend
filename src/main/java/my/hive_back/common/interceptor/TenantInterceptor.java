package my.hive_back.common.interceptor;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import my.hive.common.auth.AuthUserInfo;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.dto.Result;
import my.hive.common.tenant.TenantIsolationSupport;
import my.hive.common.utils.TokenUtil;
import my.hive_back.module.sys.model.mapper.SysUserRoleMapper;
import my.hive_back.module.tenant.mapper.TenantMapper;
import my.hive_back.module.tenant.model.entity.Tenant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;
import java.util.concurrent.TimeUnit;
/**
 * TenantInterceptor 属于小程序后端通用能力层，是请求拦截器，用于补充上下文、鉴权或租户处理。
 */
@Component
@Slf4j
public class TenantInterceptor implements HandlerInterceptor {

    @Resource
    private TenantMapper tenantMapper;

    @Resource
    private SysUserRoleMapper sysUserRoleMapper; // 只需要保留这一个 Mapper 即可

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private TenantIsolationSupport tenantIsolationSupport;

    private static final String PERM_CACHE_KEY_PREFIX = "sys:perms:";

    @Value("${auth.allow-legacy-header:false}")
    private boolean allowLegacyHeader;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String tenantCode;
        Long userId;
        String authHeader = request.getHeader("Authorization");

        if (StringUtils.isNotBlank(authHeader)) {
            AuthUserInfo authUserInfo = resolveAuthUser(authHeader);
            if (authUserInfo == null || StringUtils.isBlank(authUserInfo.getTenantCode()) || authUserInfo.getUserId() == null) {
                writeErrorResponse(response, HttpStatus.UNAUTHORIZED, 401, "登录已失效");
                return false;
            }
            tenantCode = authUserInfo.getTenantCode();
            userId = authUserInfo.getUserId();
        } else if (allowLegacyHeader) {
            tenantCode = request.getHeader("Tenant-Code");
            String userIdStr = request.getHeader("User-Id");

            if (StringUtils.isBlank(tenantCode) || StringUtils.isBlank(userIdStr)) {
                writeErrorResponse(response, HttpStatus.BAD_REQUEST, 400, "无权限");
                return false;
            }

            try {
                if (!tenantCode.matches("^[a-zA-Z0-9_]+$")) {
                    writeErrorResponse(response, HttpStatus.BAD_REQUEST, 400, "无权限");
                    return false;
                }
                userId = Long.parseLong(userIdStr);
            } catch (NumberFormatException e) {
                writeErrorResponse(response, HttpStatus.BAD_REQUEST, 400, "无权限");
                return false;
            }
        } else {
            writeErrorResponse(response, HttpStatus.UNAUTHORIZED, 401, "请先登录");
            return false;
        }

        try {
            if (!tenantCode.matches("^[a-zA-Z0-9_]+$")) {
                writeErrorResponse(response, HttpStatus.BAD_REQUEST, 400, "无权限");
                return false;
            }
        } catch (Exception e) {
            writeErrorResponse(response, HttpStatus.BAD_REQUEST, 400, "无权限");
            return false;
        }

        Tenant tenant = tenantMapper.selectByTenantCode(tenantCode);
        if (tenant == null || !Objects.equals(tenant.getStatus(), 1)) {
            writeErrorResponse(response, HttpStatus.FORBIDDEN, 403, "无权限");
            return false;
        }

        // FIELD 模式下这里是空操作；未来切换 DATABASE 模式时，需要在查询租户内权限前先绑定对应数据源。
        tenantIsolationSupport.bindTenantDatasource(tenantCode);
        Set<String> permCodes = getUserPermCodes(tenantCode, userId);
        TenantPermissionContext.init(tenantCode, userId, permCodes);
        return true;
    }

    private AuthUserInfo resolveAuthUser(String authHeader) {
        String token = authHeader.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7).trim();
        }
        return TokenUtil.parseToken(token);
    }

    /**
     * 终极版：查询用户权限（一条 SQL 搞定多表联合查询 + Redis 缓存）
     */
    private Set<String> getUserPermCodes(String tenantCode, Long userId) {
        String cacheKey = PERM_CACHE_KEY_PREFIX + tenantCode + ":" + userId;

        // 1. 尝试从 Redis 读取
        String cachedPermsStr = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.isNotBlank(cachedPermsStr)) {
            try {
                return objectMapper.readValue(cachedPermsStr, new TypeReference<Set<String>>() {});
            } catch (Exception e) {
                log.error("解析用户权限缓存异常，回退到DB查询。CacheKey: {}", cacheKey, e);
            }
        }

        // 2. 缓存未命中，调用 Mapper 执行一条连表 SQL 直接拿结果！
        Set<String> permCodes = new HashSet<>();
        List<String> permCodeList = sysUserRoleMapper.selectPermCodesByUserIdAndTenantCode(userId, tenantCode);

        if (!CollectionUtils.isEmpty(permCodeList)) {
            permCodes.addAll(permCodeList); // 转换为 Set，天然去除重复项
        }

        // 3. 写入 Redis 缓存防穿透
        try {
            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(permCodes),
                    2,
                    TimeUnit.HOURS
            );
        } catch (Exception e) {
            log.error("缓存用户权限数据异常。CacheKey: {}", cacheKey, e);
        }

        return permCodes;
    }

    private void writeErrorResponse(HttpServletResponse response, HttpStatus httpStatus,
                                    Integer bizCode, String msg) throws Exception {
        Result<Void> errorResult = Result.fail(bizCode, msg);
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(httpStatus.value());
        try (var writer = response.getWriter()) {
            objectMapper.writeValue(writer, errorResult);
            writer.flush();
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // Always clear routing state to avoid thread reuse leaking another tenant's datasource.
        tenantIsolationSupport.clearTenantDatasource();
        TenantPermissionContext.clear();
    }
}

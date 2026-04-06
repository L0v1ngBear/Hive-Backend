package my.hive_back.common.interceptor;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import my.hive_back.common.context.TenantPermissionContext;
import my.hive_back.common.dto.Result;
import my.hive_back.module.sys.model.mapper.SysUserRoleMapper;
import my.hive_back.module.tenant.mapper.TenantMapper;
import my.hive_back.module.tenant.model.entity.Tenant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.*;
import java.util.concurrent.TimeUnit;

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

    private static final String PERM_CACHE_KEY_PREFIX = "sys:perms:";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String tenantCode = request.getHeader("Tenant-Code");
        String userIdStr = request.getHeader("User-Id");

        if (StringUtils.isBlank(tenantCode)) {
            writeErrorResponse(response, HttpStatus.BAD_REQUEST, 400, "无权限");
            return false;
        }

        if (StringUtils.isBlank(userIdStr)) {
            writeErrorResponse(response, HttpStatus.BAD_REQUEST, 400, "无权限");
            return false;
        }

        try {
            if (!tenantCode.matches("^[a-zA-Z0-9_]+$")) {
                writeErrorResponse(response, HttpStatus.BAD_REQUEST, 400, "无权限");
                return false;
            }
            Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            writeErrorResponse(response, HttpStatus.BAD_REQUEST, 400, "无权限");
            return false;
        }

        Tenant tenant = tenantMapper.selectByTenantCode(tenantCode);
        if (tenant == null) {
            writeErrorResponse(response, HttpStatus.FORBIDDEN, 403, "无权限");
            return false;
        }

        Long userId = Long.parseLong(userIdStr);
        Set<String> permCodes = getUserPermCodes(tenantCode, userId);

        TenantPermissionContext.init(tenantCode, userId, permCodes);
        return true;
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
        TenantPermissionContext.clear();
    }
}
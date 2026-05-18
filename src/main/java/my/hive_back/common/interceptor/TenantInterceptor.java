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
import my.hive.common.redis.HiveRedisKeyBuilder;
import my.hive.common.tenant.TenantIsolationSupport;
import my.hive.common.utils.ResponseEncryptUtil;
import my.hive.common.utils.TokenUtil;
import my.hive_back.common.tenant.BoundedTenantProperties;
import my.hive_back.module.sys.model.mapper.SysUserRoleMapper;
import my.hive_back.module.tenant.mapper.TenantMapper;
import my.hive_back.module.tenant.model.entity.Tenant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 小程序租户与权限上下文拦截器。
 */
@Component
@Slf4j
public class TenantInterceptor implements HandlerInterceptor {

    private static final long TENANT_STATUS_CACHE_MINUTES = 10L;
    private static final long TENANT_STATUS_NEGATIVE_CACHE_SECONDS = 60L;
    private static final long USER_PERMISSION_CACHE_MINUTES = 30L;
    @Resource
    private TenantMapper tenantMapper;

    @Resource
    private SysUserRoleMapper sysUserRoleMapper;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private TenantIsolationSupport tenantIsolationSupport;

    @Resource
    private HiveRedisKeyBuilder redisKeyBuilder;

    @Resource
    private ResponseEncryptUtil responseEncryptUtil;

    @Resource
    private BoundedTenantProperties boundedTenantProperties;

    @Value("${auth.allow-legacy-header:false}")
    private boolean allowLegacyHeader;

    @Value("${auth.token.renew-enabled:true}")
    private boolean tokenRenewEnabled;

    @Value("${auth.token.renew-before-minutes:120}")
    private long tokenRenewBeforeMinutes;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String tenantCode;
        Long userId;
        String authHeader = request.getHeader("Authorization");
        AuthUserInfo authUserInfo = null;

        if (StringUtils.isNotBlank(authHeader)) {
            authUserInfo = resolveAuthUser(authHeader);
            if (authUserInfo == null || authUserInfo.getUserId() == null) {
                writeErrorResponse(response, HttpStatus.UNAUTHORIZED, 401, "登录已失效");
                return false;
            }
            tenantCode = normalizeTenantCode(authUserInfo.getTenantCode());
            if (tenantCode == null) {
                tenantCode = defaultTenantCode();
            }
            userId = authUserInfo.getUserId();
        } else if (allowLegacyHeader) {
            tenantCode = normalizeTenantCode(request.getHeader("Tenant-Code"));
            String userIdStr = request.getHeader("User-Id");

            if (tenantCode == null) {
                tenantCode = defaultTenantCode();
            }
            if (StringUtils.isBlank(userIdStr)) {
                writeErrorResponse(response, HttpStatus.BAD_REQUEST, 400, "无权限");
                return false;
            }

            try {
                userId = Long.parseLong(userIdStr);
            } catch (NumberFormatException e) {
                writeErrorResponse(response, HttpStatus.BAD_REQUEST, 400, "无权限");
                return false;
            }
        } else {
            writeErrorResponse(response, HttpStatus.UNAUTHORIZED, 401, "请先登录");
            return false;
        }

        if (!isValidTenantCode(tenantCode)) {
            writeErrorResponse(response, HttpStatus.BAD_REQUEST, 400, "无权限");
            return false;
        }

        if (!boundedTenantProperties.isTenantAllowed(tenantCode)) {
            writeErrorResponse(response, HttpStatus.FORBIDDEN, 403, "当前组织不在系统允许范围内");
            return false;
        }

        if (!isTenantEnabled(tenantCode)) {
            writeErrorResponse(response, HttpStatus.FORBIDDEN, 403, "租户不可用");
            return false;
        }

        tenantIsolationSupport.bindTenantDatasource(tenantCode);
        Set<String> permCodes = getUserPermCodes(tenantCode, userId);
        TenantPermissionContext.init(tenantCode, userId, permCodes);
        maybeRenewToken(response, authUserInfo);
        return true;
    }

    private AuthUserInfo resolveAuthUser(String authHeader) {
        String token = authHeader.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7).trim();
        }
        return TokenUtil.parseToken(token);
    }

    private void maybeRenewToken(HttpServletResponse response, AuthUserInfo authUserInfo) {
        if (!tokenRenewEnabled || response.isCommitted() || !TokenUtil.shouldRenew(authUserInfo, tokenRenewBeforeMinutes)) {
            return;
        }
        String tenantCode = normalizeTenantCode(authUserInfo.getTenantCode());
        String renewedToken = TokenUtil.createToken(authUserInfo.getUserId(), tenantCode == null ? defaultTenantCode() : tenantCode);
        AuthUserInfo renewedUserInfo = TokenUtil.parseToken(renewedToken);
        if (renewedUserInfo == null || renewedUserInfo.getExpireAt() == null) {
            return;
        }
        response.setHeader(TokenUtil.HEADER_RENEWED_TOKEN, renewedToken);
        response.setHeader(TokenUtil.HEADER_RENEWED_EXPIRE_AT, String.valueOf(renewedUserInfo.getExpireAt()));
        response.setHeader(TokenUtil.HEADER_RENEWED_RESPONSE_KEY, responseEncryptUtil.buildResponseKey(renewedToken));
    }

    private Set<String> getUserPermCodes(String tenantCode, Long userId) {
        String cacheKey = redisKeyBuilder.cache("mini", "perm", tenantCode, String.valueOf(userId));

        String cachedPermsStr = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.isNotBlank(cachedPermsStr)) {
            try {
                return objectMapper.readValue(cachedPermsStr, new TypeReference<Set<String>>() {});
            } catch (Exception e) {
                log.error("parse mini user permission cache failed, cacheKey={}", cacheKey, e);
            }
        }

        Set<String> permCodes = new HashSet<>();
        List<String> permCodeList = sysUserRoleMapper.selectPermCodesByUserIdAndTenantCode(userId, tenantCode);
        if (!CollectionUtils.isEmpty(permCodeList)) {
            permCodes.addAll(permCodeList);
        }

        try {
            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(permCodes),
                    USER_PERMISSION_CACHE_MINUTES,
                    TimeUnit.MINUTES
            );
        } catch (Exception e) {
            log.error("write mini user permission cache failed, cacheKey={}", cacheKey, e);
        }

        return permCodes;
    }

    private boolean isTenantEnabled(String tenantCode) {
        String cacheKey = redisKeyBuilder.cache("tenant", "status", tenantCode);
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if ("1".equals(cached)) {
                return true;
            }
            if ("0".equals(cached)) {
                return false;
            }
        } catch (Exception e) {
            log.warn("read tenant status cache failed, tenantCode={}", tenantCode, e);
        }

        Tenant tenant = tenantMapper.selectByTenantCode(tenantCode);
        boolean enabled = isTenantUsable(tenant);
        try {
            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    enabled ? "1" : "0",
                    enabled ? TENANT_STATUS_CACHE_MINUTES : TENANT_STATUS_NEGATIVE_CACHE_SECONDS,
                    enabled ? TimeUnit.MINUTES : TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.warn("write tenant status cache failed, tenantCode={}", tenantCode, e);
        }
        return enabled;
    }

    private boolean isTenantUsable(Tenant tenant) {
        if (tenant == null || Objects.equals(tenant.getDeleted(), 1) || !Objects.equals(tenant.getStatus(), 1)) {
            return false;
        }
        String subscriptionStatus = tenant.getSubscriptionStatus();
        if (StringUtils.isNotBlank(subscriptionStatus)) {
            String normalized = subscriptionStatus.trim().toUpperCase(Locale.ROOT);
            if ("EXPIRED".equals(normalized) || "SUSPENDED".equals(normalized)) {
                return false;
            }
        }
        LocalDateTime endTime = tenant.getSubscriptionEndTime();
        return endTime == null || !endTime.isBefore(LocalDateTime.now());
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

    private String normalizeTenantCode(String tenantCode) {
        if (tenantCode == null || tenantCode.trim().isEmpty()) {
            return null;
        }
        return tenantCode.trim();
    }

    private String defaultTenantCode() {
        return boundedTenantProperties.defaultTenantCode();
    }

    private boolean isValidTenantCode(String tenantCode) {
        return tenantCode != null && tenantCode.matches("^[a-zA-Z0-9_]+$");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        tenantIsolationSupport.clearTenantDatasource();
        TenantPermissionContext.clear();
    }
}

package my.hive_back.common.context;

import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TenantPermissionContext 属于小程序后端通用能力层，负责请求上下文的读写与传递。
 */
public class TenantPermissionContext {

    // 线程本地存储：存储租户、用户、权限完整信息
    private static final ThreadLocal<ConcurrentHashMap<String, Object>> THREAD_LOCAL = new ThreadLocal<>();

    // 上下文key常量
    private static final String KEY_TENANT_CODE = "tenantCode";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_PERM_CODES = "permCodes";

    private static final ThreadLocal<Boolean> IGNORE_TENANT = new ThreadLocal<>();

    public static void setIgnoreTenant(boolean ignore) {
        IGNORE_TENANT.set(ignore);
    }

    public static boolean isIgnoreTenant() {
        return Boolean.TRUE.equals(IGNORE_TENANT.get());
    }

    public static void clearIgnore() {
        IGNORE_TENANT.remove();
    }

    /**
     * 初始化上下文（核心方法：拦截器中调用）
     */
    public static void init(String tenantCode, Long userId, Set<String> permCodes) {
        ConcurrentHashMap<String, Object> context = new ConcurrentHashMap<>();
        context.put(KEY_TENANT_CODE, tenantCode);
        context.put(KEY_USER_ID, userId);
        context.put(KEY_PERM_CODES, permCodes);
        THREAD_LOCAL.set(context);

    }

    /**
     * 核心：校验是否有指定权限 (支持超级管理员 *，以及多级通配符如 sys:*, sys:user:*)
     * @param permCode 权限编码（如：sys:user:add）
     */
    public static boolean hasPermission(String permCode) {
        if (permCode == null || permCode.trim().isEmpty()) {
            return false; // 如果未指定具体权限，默认不放行
        }

        ConcurrentHashMap<String, Object> context = THREAD_LOCAL.get();
        if (context == null) {
            return false;
        }

        @SuppressWarnings("unchecked")
        Set<String> permCodes = (Set<String>) context.get(KEY_PERM_CODES);
        if (CollectionUtils.isEmpty(permCodes)) {
            return false;
        }

        // 1. 上帝模式：如果有全局通配符，直接放行 (超级管理员特权)
        if (permCodes.contains("*") || permCodes.contains("*:*")) {
            return true;
        }

        // 2. 精确匹配：拥有指定的绝对权限
        if (permCodes.contains(permCode)) {
            return true;
        }

        // 3. 多级通配符逐级降级匹配
        // 例如需要 sys:user:add 权限，会依次去 Set 里找是否存在：
        // -> sys:user:*
        // -> sys:*
        int lastColonIndex = permCode.lastIndexOf(":");
        while (lastColonIndex > 0) {
            String prefix = permCode.substring(0, lastColonIndex);
            if (permCodes.contains(prefix + ":*")) {
                return true;
            }
            // 继续往前找上一级的冒号
            lastColonIndex = prefix.lastIndexOf(":");
        }

        // 都没匹配上，拦截
        return false;
    }


    // ---------- 兼容原有方法 ----------
    /**
     * 获取当前租户编码（和TenantContextHolder.getTenantCode()效果一致）
     */
    public static String getTenantCode() {
        // 优先从本类上下文获取，兼容旧逻辑
        ConcurrentHashMap<String, Object> context = THREAD_LOCAL.get();
        if (context != null) {
            return (String) context.get(KEY_TENANT_CODE);
        }
        return null;
    }

    /**
     * 获取当前用户ID
     */
    public static Long getUserId() {
        ConcurrentHashMap<String, Object> context = THREAD_LOCAL.get();
        return context == null ? null : (Long) context.get(KEY_USER_ID);
    }

    @SuppressWarnings("unchecked")
    public static Set<String> getPermCodes() {
        ConcurrentHashMap<String, Object> context = THREAD_LOCAL.get();
        if (context == null) {
            return Collections.emptySet();
        }
        Set<String> permCodes = (Set<String>) context.get(KEY_PERM_CODES);
        return permCodes == null ? Collections.emptySet() : permCodes;
    }

    /**
     * 清空上下文（必须调用，防止线程污染）
     */
    public static void clear() {
        THREAD_LOCAL.remove();
    }
}

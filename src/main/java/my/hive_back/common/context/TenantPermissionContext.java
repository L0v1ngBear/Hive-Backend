package my.hive_back.common.context;

import org.springframework.util.CollectionUtils;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TenantPermissionContext {

    // 线程本地存储：存储租户、用户、权限完整信息
    private static final ThreadLocal<ConcurrentHashMap<String, Object>> THREAD_LOCAL = new ThreadLocal<>();

    // 上下文key常量
    private static final String KEY_TENANT_CODE = "tenantCode";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_PERM_CODES = "permCodes";

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
     * 核心：校验是否有指定权限
     * @param permCode 权限编码（如：order:add、order:*）
     */
    public static boolean hasPermission(String permCode) {
        ConcurrentHashMap<String, Object> context = THREAD_LOCAL.get();
        if (context == null) {
            return false;
        }

        @SuppressWarnings("unchecked")
        Set<String> permCodes = (Set<String>) context.get(KEY_PERM_CODES);
        if (CollectionUtils.isEmpty(permCodes)) {
            return false;
        }

        // 支持通配符：如 order:* 匹配所有订单相关权限
        if (permCode.endsWith(":*")) {
            String prefix = permCode.replace(":*", "");
            return permCodes.stream().anyMatch(p -> p.startsWith(prefix + ":"));
        }
        return permCodes.contains(permCode);
    }

    /**
     * 简化版：校验权限并抛出异常（业务层直接用，无需写if）
     */
    public static void checkPermission(String permCode) {
        checkPermission(permCode, "无操作权限，请联系租户管理员");
    }

    public static void checkPermission(String permCode, String message) {
        if (!hasPermission(permCode)) {
            throw new RuntimeException(message); // 后续用全局异常处理器捕获
        }
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

    /**
     * 清空上下文（必须调用，防止线程污染）
     */
    public static void clear() {
        THREAD_LOCAL.remove();
    }
}
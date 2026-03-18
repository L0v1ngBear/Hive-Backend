package my.hive_back.common.exception;

/**
 * 自定义权限拒绝异常（替代 Spring Security 的 AccessDeniedException）
 */
public class PermissionDeniedException extends RuntimeException {

    // 无参构造
    public PermissionDeniedException() {
        super();
    }

    // 带错误信息的构造
    public PermissionDeniedException(String message) {
        super(message);
    }

    // 带错误信息和异常原因的构造（可选）
    public PermissionDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
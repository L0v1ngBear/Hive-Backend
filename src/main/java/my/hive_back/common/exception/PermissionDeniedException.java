package my.hive_back.common.exception;

/**
 * PermissionDeniedException 属于小程序后端通用能力层，定义异常语义或异常处理行为。
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

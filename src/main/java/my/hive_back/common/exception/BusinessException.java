package my.hive_back.common.exception;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BusinessException extends RuntimeException {
    /** 业务响应码 */
    private Integer code;
    /** 错误提示 */
    private String msg;

    // 构造方法
    public BusinessException(Integer code, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
    }

    // 重载：默认业务码400
    public BusinessException(String msg) {
        this(400, msg);
    }

}
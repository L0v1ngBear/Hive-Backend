package my.hive_back.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
/**
 * Result 属于小程序后端通用能力层，定义通用传输对象。
 */
@Data
public class Result<T> {

    private Integer code;

    private String msg;

    private T data;

    @JsonIgnore
    private Boolean encrypted = false;

    @JsonIgnore
    private String alg;

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg("success");
        result.setData(data);
        result.setEncrypted(false);
        return result;
    }

    public static <T> Result<T> fail(Integer code, String msg) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        result.setData(null);
        result.setEncrypted(false);
        return result;
    }

    public static <T> Result<T> fail(String msg) {
        return fail(500, msg);
    }
}

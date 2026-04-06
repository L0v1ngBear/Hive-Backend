package my.hive_back.common.dto;

import lombok.Data;

// 统一返回格式
@Data
public class Result<T> {
    // 状态码：200成功，其他失败
    private Integer code;
    // 返回消息
    private String msg;
    // 返回数据
    private T data;

    // 成功响应
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg("success");
        result.setData(data);
        return result;
    }

    // 失败响应
    public static <T> Result<T> fail(Integer code, String msg) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        result.setData(null);
        return result;
    }

    public static <T> Result<T> fail(String msg) {
        return fail(500, msg);
    }
}
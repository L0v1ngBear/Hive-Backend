package my.hive_back.module.auth.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
/**
 * LoginRequest 属于小程序后端认证模块，定义入参结构。
 */
@Data
public class LoginRequest {

    @NotBlank(message = "租户编码不能为空")
    private String tenantCode;

    @NotBlank(message = "账号不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}

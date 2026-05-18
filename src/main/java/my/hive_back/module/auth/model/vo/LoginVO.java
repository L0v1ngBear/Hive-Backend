package my.hive_back.module.auth.model.vo;

import lombok.Data;
/**
 * LoginVO 属于小程序后端认证模块，定义出参结构。
 */
@Data
public class LoginVO {

    private String token;

    private Long expireAt;

    private Long userId;

    private String userName;

    private String phone;

    private String departmentName;

    private String position;

    private String tenantCode;

    private String tenantName;

    private String responseKey;
}

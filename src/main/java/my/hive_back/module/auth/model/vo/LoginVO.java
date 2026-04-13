package my.hive_back.module.auth.model.vo;

import lombok.Data;

@Data
public class LoginVO {

    private String token;

    private Long expireAt;

    private Long userId;

    private String userName;

    private String phone;

    private String position;

    private String tenantCode;
}

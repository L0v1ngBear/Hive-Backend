package my.hive_back.common.auth;

import lombok.Data;

@Data
public class AuthUserInfo {

    /**
     * 当前登录用户ID
     */
    private Long userId;

    /**
     * 当前租户编码
     */
    private String tenantCode;

    /**
     * token 过期时间（秒级时间戳）
     */
    private Long expireAt;
}

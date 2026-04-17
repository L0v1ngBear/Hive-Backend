package my.hive_back.common.auth;

import lombok.Data;
/**
 * AuthUserInfo 属于小程序后端通用能力层，提供认证或鉴权支撑逻辑。
 */
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

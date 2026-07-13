package my.hive_back.module.auth.model.dto;

import lombok.Data;

/**
 * 微信手机号一键登录入参。
 *
 * 小程序端通过按钮 open-type="getPhoneNumber" 获取 phoneCode，
 * 后端再用该一次性 code 调微信接口换取手机号，不需要短信验证码。
 */
@Data
public class WechatLoginRequest {

    /**
     * 微信手机号授权 code，对应 getPhoneNumber 事件里的 e.detail.code。
     */
    private String phoneCode;

    /**
     * 可选租户编码。为空时后端会按手机号在全部启用租户中定位用户。
     */
    private String tenantCode;
}

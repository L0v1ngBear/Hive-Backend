package my.hive_back.module.wechat.model.vo;

import lombok.Data;

/**
 * 微信手机号解析结果。
 */
@Data
public class WechatPhoneInfoVO {

    /**
     * 完整手机号，通常为 11 位大陆手机号。
     */
    private String phoneNumber;

    /**
     * 不带区号的手机号。
     */
    private String purePhoneNumber;

    /**
     * 国家/地区码，例如 86。
     */
    private String countryCode;
}

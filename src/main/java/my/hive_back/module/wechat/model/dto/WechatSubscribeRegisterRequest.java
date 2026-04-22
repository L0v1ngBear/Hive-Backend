package my.hive_back.module.wechat.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 小程序订阅消息授权登记入参，前端通过 wx.login 获取 code，再带上模板授权结果。
 */
@Data
public class WechatSubscribeRegisterRequest {

    private String code;

    private List<TemplateSubscribeStatus> subscriptions;

    @Data
    public static class TemplateSubscribeStatus {
        private String templateId;
        private String status;
    }
}

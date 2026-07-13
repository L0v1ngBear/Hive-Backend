package my.hive_back.module.wechat.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 小程序订阅消息授权登记入参，前端通过 wx.login 获取 code，再带上模板授权结果。
 */
@Data
public class WechatSubscribeRegisterRequest {

    @NotBlank(message = "微信登录 code 不能为空")
    @Size(max = 128, message = "微信登录 code 长度不能超过128位")
    private String code;

    @Valid
    @NotEmpty(message = "订阅模板授权结果不能为空")
    private List<TemplateSubscribeStatus> subscriptions;

    @Data
    public static class TemplateSubscribeStatus {
        @NotBlank(message = "订阅模板ID不能为空")
        @Size(max = 128, message = "订阅模板ID长度不能超过128位")
        private String templateId;

        @NotBlank(message = "订阅授权状态不能为空")
        @Size(max = 20, message = "订阅授权状态长度不能超过20位")
        private String status;
    }
}

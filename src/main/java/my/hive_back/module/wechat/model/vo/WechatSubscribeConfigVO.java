package my.hive_back.module.wechat.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 小程序订阅消息配置出参，前端据此发起 wx.requestSubscribeMessage。
 */
@Data
public class WechatSubscribeConfigVO {

    private Boolean enabled;

    private List<String> templateIds;

    private String todoTemplateId;
}

package my.hive_back.module.wechat.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 微信订阅消息用户授权记录，用于保存用户 openid 与模板授权状态。
 */
@Data
@TableName("wechat_subscribe_user")
public class WechatSubscribeUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantCode;

    private Long userId;

    private String openid;

    private String templateId;

    /**
     * 授权状态：accept-同意，reject-拒绝，ban-后台封禁/不可用。
     */
    private String subscribeStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

package my.hive_back.module.notification.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小程序企业公告展示模型。
 */
@Data
public class AnnouncementVO {

    private Long id;

    private String bizId;

    private String title;

    private String content;

    private String level;

    private Integer readFlag;

    private LocalDateTime readTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

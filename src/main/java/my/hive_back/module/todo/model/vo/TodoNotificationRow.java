package my.hive_back.module.todo.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统一通知表中的小程序待办投影。
 */
@Data
public class TodoNotificationRow {

    private Long id;

    private String bizType;

    private String bizId;

    private String title;

    private String content;

    private String level;

    private String route;

    private String taskStatus;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

package my.hive_back.module.todo.model.vo;

import lombok.Data;

/**
 * 小程序待办事项出参，前端根据 type 和 route 直接跳转到对应业务页。
 */
@Data
public class TodoItemVO {

    /**
     * 待办唯一键，使用业务类型加业务单号拼接，避免不同单据编号冲突。
     */
    private String id;

    /**
     * 业务类型：leave/finance/production/sales/outbound/badProduct。
     */
    private String type;

    /**
     * 前端展示标签。
     */
    private String tag;

    /**
     * 待办标题。
     */
    private String title;

    /**
     * 待办说明。
     */
    private String content;

    /**
     * 业务单号。
     */
    private String bizId;

    /**
     * 跳转页面地址。
     */
    private String route;

    /**
     * 时间文本，保持首页展示简洁。
     */
    private String time;

    /**
     * 排序用时间戳，前端无需展示。
     */
    private Long sortTime;
}

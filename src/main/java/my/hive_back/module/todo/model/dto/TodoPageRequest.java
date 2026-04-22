package my.hive_back.module.todo.model.dto;

import lombok.Data;

/**
 * 小程序待办分页查询入参，统一承接首页待办和完整待办列表查询。
 */
@Data
public class TodoPageRequest {

    /**
     * 页码，从 1 开始。
     */
    private Long pageNum = 1L;

    /**
     * 每页条数。
     */
    private Long pageSize = 20L;

    /**
     * 待办类型：all/approval/order/print/quality。
     */
    private String type = "all";
}

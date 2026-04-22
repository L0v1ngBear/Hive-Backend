package my.hive_back.module.order.model.dto;

import lombok.Data;

/**
 * 订单列表查询的公共入参。
 *
 * <p>销售订单和生产订单列表目前都需要状态筛选、关键词搜索和分页参数，
 * 所以统一抽到父类，避免两个 DTO 重复维护同一批字段。</p>
 */
@Data
public class BaseOrderListRequest {

    /**
     * 订单状态编码；为空时查询全部状态。
     */
    private String status;

    /**
     * 关键词搜索，通常匹配订单号、客户、项目或商品描述。
     */
    private String keyWord;

    /**
     * 当前页码，从 1 开始。
     */
    private Integer pageNum = 1;

    /**
     * 每页条数。
     */
    private Integer pageSize = 20;
}

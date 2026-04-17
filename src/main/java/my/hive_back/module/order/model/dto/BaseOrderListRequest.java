package my.hive_back.module.order.model.dto;

import lombok.Data;
/**
 * BaseOrderListRequest 属于小程序后端订单模块，定义入参结构。
 */
@Data
public class BaseOrderListRequest {
    private String status;
    private String keyWord;
    private Integer pageNum = 1;
    private Integer pageSize = 20;
}

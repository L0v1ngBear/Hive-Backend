package my.hive_back.module.order.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
/**
 * ProductionOrderStatusLogVO 属于小程序后端订单模块，定义出参结构。
 */
@Data
public class ProductionOrderStatusLogVO {

    private Long id;

    private String tenantCode;

    private String orderId;

    private String oldStatus;

    private String newStatus;

    private String operateType;

    private String remark;

    private String operator;

    private LocalDateTime createTime;
}

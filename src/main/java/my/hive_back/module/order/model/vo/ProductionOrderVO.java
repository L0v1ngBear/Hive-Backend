package my.hive_back.module.order.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
/**
 * ProductionOrderVO 属于小程序后端订单模块，定义出参结构。
 */
@Data
public class ProductionOrderVO {

    private Long id;

    private String orderId;

    private String salesOrderId;

    private String status;

    private String modelCode;

    private Float weight;

    private Float spec;

    private Integer quantity;

    /** 当前生产工序：0-整经，1-浆纱，2-织造，3-验布，4-卷布 */
    private Integer process;

    private String customerName;

    private String projectName;

    private LocalDateTime deliveryDate;

    private LocalDateTime createTime;
}

package my.hive_back.module.order.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductionOrderVO {


    /**
     * 主键ID
     */
    private Long id;

    /**
     * 生产订单编号（如TZ20260212001）
     */
    private String orderId;

    /**
     * 订单状态：OrderStatusEnum pending_confirm-待确认，pending_material-备料中，producing-生产中，pending_ship-待发货，shipped-已发货，completed-已完成
     */
    private String status;

    /** 
     * 面料型号（如T800-210）
     */
    private String modelCode;

    /**
     * 克重
     */
    private BigDecimal weight;

    /**
     * 幅宽（cm）
     */
    private BigDecimal spec;

    /**
     * 数量
     */
    private Integer quantity;


    /**
     * 当前生产工序：ProductionProcessEnum 0-整经，1-浆纱，2-织造，3-验布，4-卷布
     * 允许null
     */
    private Integer process;

    private LocalDateTime deliveryDate;

}

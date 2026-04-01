package my.hive_back.module.order.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产订单实体类
 */
@TableName("production_order")
@Data
public class ProductionOrder {

    /**
     * 主键ID
     */
    @TableId(type = IdType.INPUT)
    private Long id;

    @TableField("tenant_code")
    private String tenantCode;

    /**
     * 生产订单编号（如TZ20260212001）
     */
    @TableField("order_id")
    private String orderId;

    @TableField("sales_order_id")
    private String salesOrderId;

    /**
     * 订单状态：OrderStatusEnum pending_confirm-待确认，pending_material-备料中，producing-生产中，pending_ship-待发货，shipped-已发货，completed-已完成
     */
    @TableField("status")
    private String status;

    /**
     * 面料型号（如T800-210）
     */
    @TableField("model_code")
    private String modelCode;


    /**
     * 克重
     */
    @TableField("weight")
    private Float weight;

    /**
     * 幅宽（cm）
     */
    @TableField("spec")
    private String spec;


    /**
     * 数量
     */
    @TableField("quantity")
    private Integer quantity;

    /**
     * 当前生产工序：ProductionProcessEnum 0-整经，1-浆纱，2-织造，3-验布，4-卷布
     */
    @TableField("process")
    private Integer process;


    @TableField("customer_name")
    private String customerName;

    @TableField("project_name")
    private String projectName;

    /**
     * 预计交付日期
     */
    @TableField("delivery_date")
    private LocalDateTime deliveryDate;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 创建人
     */
    @TableField(value = "creator", fill = FieldFill.INSERT)
    private Long creator;

    /**
     * 更新人
     */
    @TableField(value = "updater", fill = FieldFill.INSERT_UPDATE)
    private Long updater;

}

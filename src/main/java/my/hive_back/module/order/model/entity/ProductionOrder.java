package my.hive_back.module.order.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

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

    // Link back to the source sales order when a production order is auto-created.
    @TableField("sales_order_id")
    private String salesOrderId;

    /**
     * 订单状态：OrderStatusEnum pending_confirm-待确认，pending_material-备料中，producing-生产中，pending_ship-待发货，shipped-已发货，completed-已完成
     */
    @TableField("status")
    private String status;

    @TableField("order_category")
    private String orderCategory;

    /**
     * 面料型号（如T800-210）
     */
    // The current hive.production_order table stores this field as `model`.
    @TableField("model")
    private String modelCode;


    /**
     * 克重
     */
    @TableField("weight")
    private Float weight;

    /**
     * 幅宽（cm）
     */
    // The current hive.production_order table stores width/spec in the `width` column.
    @TableField("width")
    private Float spec;


    /**
     * 数量
     */
    @TableField("quantity")
    private Integer quantity;

    /**
     * 当前生产工序：ProcessEnum
     */
    @TableField("process")
    private Integer process;


    @TableField("customer_name")
    private String customerName;

    @TableField("project_name")
    private String projectName;

    @TableField("brand_name")
    private String brandName;

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
    private String creator;

    /**
     * 更新人
     */
    @TableField(value = "updater", fill = FieldFill.INSERT_UPDATE)
    private String updater;

}

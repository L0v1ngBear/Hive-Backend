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
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_code")
    private String tenantCode;

    /**
     * 生产订单编号（如TZ20260212001）
     */
    @TableField("order_id")
    private String orderId;


    /**
     * 订单状态：OrderStatusEnum pending_confirm-待确认，pending_material-备料中，producing-生产中，pending_ship-待发货，shipped-已发货，completed-已完成
     */
    @TableField("status")
    private String status;

    /**
     * 面料型号（如T800-210）
     */
    @TableField("model")
    private String model;

    /**
     * 面料名称（如涤纶弹力布）
     */
    @TableField("fabric")
    private String fabric;

    /**
     * 克重
     */
    @TableField("weight")
    private BigDecimal weight;

    /**
     * 幅宽（cm）
     */
    @TableField("width")
    private BigDecimal width;

    /**
     * 颜色（如藏青色）
     */
    @TableField("color")
    private String color;

    /**
     * 数量
     */
    @TableField("quantity")
    private Integer quantity;

    /**
     * 单价（元）
     */
    @TableField("price")
    private BigDecimal price;

    /**
     * 总价（自动计算）
     */
    @TableField("total_amount")
    private BigDecimal totalAmount;

    /**
     * 当前生产工序：ProductionProcessEnum 0-整经，1-浆纱，2-织造，3-验布，4-卷布
     */
    @TableField("process")
    private Integer process;

    /**
     * 客户名称
     */
    @TableField("customer_id")
    private String customerId;

    @TableField("customer_name")
    private String customerName;

    @TableField("project_name")
    private String projectName;

    @TableField("contactPhone")
    private String contactPhone;

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

package my.hive_back.module.order.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("sales_order")
@Data
public class SalesOrder {

    /**
     * 订单ID（主键，如：SO20260331001）
     */
    @TableId(type = IdType.INPUT)
    private String orderId;

    @TableField("tenant_code")
    private String tenantCode;

    /**
     * 订单状态
     * pending_pay: 待收款
     * pending_ship: 待发货
     * shipped: 已发货
     * completed: 已完成
     * cancelled: 已取消
     */
    private String status;

    @TableField("order_category")
    private String orderCategory;

    /**
     * 客户名称
     */
    private String customerName;

    /**
     * 项目名称
     */
    @TableField("project_name")
    private String projectName;

    @TableField("brand_name")
    private String brandName;

    /**
     * 聚合后的商品说明，方便管理端列表快速展示订单内容。
     */
    @TableField("goods_desc")
    private String goodsDesc;

    /**
     * 聚合后的订单总数量。
     */
    @TableField("total_quantity")
    private Integer totalQuantity;

    /**
     * 预计发货日期
     */
    private String deliveryDate;

    /**
     * 物流公司
     */
    private String expressCompany;

    /**
     * 物流单号
     */
    private String expressNo;

    /**
     * 是否需要发票 (0-否，1-是)
     */
    private Integer isInvoice;

    /**
     * 订单创建人
     */
    private String creator;

    private String updater;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

package my.hive_back.module.order.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("sales_order")
@Data
public class SalesOrder {
    /**
     * 订单ID（主键）
     */
    @TableId(type = IdType.INPUT) // 订单号手动生成，不使用自增
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

    /**
     * 客户名称
     */
    private String customerName;

    /**
     * 客户联系方式（扩展字段）
     */
    private String customerPhone;

    /**
     * 商品描述（列表页展示用）
     */
    private String goodsDesc;

    /**
     * 订单总金额
     */
    private BigDecimal totalAmount;

    /**
     * 订单总数量
     */
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
     * 订单创建时间
     */
    private LocalDateTime createTime;

    /**
     * 订单创建人
     */
    private String creator;

    /**
     * 订单更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 操作备注
     */
    private String remark;
}

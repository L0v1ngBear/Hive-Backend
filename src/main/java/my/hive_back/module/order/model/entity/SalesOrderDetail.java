package my.hive_back.module.order.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售订单明细实体，对应销售订单下的型号、规格和数量等子项数据。
 */
@Data
@TableName("sales_order_detail")
public class SalesOrderDetail {

    /**
     * 明细主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属销售订单编号。
     */
    @TableField("order_id")
    private String orderId;

    /**
     * 当前仍按字段隔离租户时，子表也必须保留租户字段。
     */
    @TableField("tenant_code")
    private String tenantCode;

    /**
     * 产品型号编码。
     */
    @TableField("model_code")
    private String modelCode;

    /**
     * 规格描述。
     */
    private String spec;

    /**
     * 数量。
     */
    private BigDecimal quantity;

    /**
     * 创建时间。
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}

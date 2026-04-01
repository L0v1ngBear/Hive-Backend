package my.hive_back.module.order.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售订单 - 明细子表 (Order Line/Detail)
 */
@TableName("sales_order_detail")
@Data
public class SalesOrderDetail {

    /**
     * 明细主键ID
     */
    @TableId(type = IdType.AUTO) // 雪花算法自动生成ID
    private Long id;

    /**
     * 关联的主订单ID (外键逻辑)
     */
    private String orderId;

    /**
     * 商品/布匹型号代码 (如: T800-210)
     */
    private String modelCode;

    /**
     * 规格 (如: 门幅/克重)
     */
    private String spec;

    /**
     * 需求数量(米)
     */
    private BigDecimal quantity;

    /**
     * 商品单价
     */
    private BigDecimal unitPrice;

    /**
     * 该明细行总价 (quantity * unitPrice)
     */
    private BigDecimal lineAmount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
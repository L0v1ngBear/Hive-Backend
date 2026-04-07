package my.hive_back.module.inventory.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 出库单明细表 (记录每一次扫码出库的实体布匹)
 */
@Data
@TableName("outbound_item")
public class OutboundItem {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户编号 (冗余租户字段，方便单表做租户隔离查询)
     */
    private String tenantCode;

    /**
     * 关联的主表出库单ID (外键)
     */
    private Long orderId;

    /**
     * 布匹条码
     */
    private String barcode;

    /**
     * 商品型号代码
     */
    private String modelCode;

    /**
     * 规格 (如门幅、克重等)
     */
    private Float spec;

    /**
     * 本次出库米数
     */
    private Float meters;

    /**
     * 单价 (用于打印单据上的金额展示)
     */
    private BigDecimal price;

    /**
     * 总金额 (单价 * 出库米数)
     */
    private BigDecimal totalAmount;
}
package my.hive_back.module.inventory.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("outbound_item")
public class OutboundItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantCode;

    private Long orderId;

    private String barcode;

    private String modelCode;

    private Float spec;

    private Float meters;

    private BigDecimal price;

    private BigDecimal totalAmount;

    /** 客户端幂等请求号 */
    private String requestId;
}
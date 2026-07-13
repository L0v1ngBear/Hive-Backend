package my.hive_back.module.inventory.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
/**
 * OutboundOrder 属于小程序后端库存模块，定义持久化实体结构，用于表字段映射。
 */
@Data
@TableName("outbound_order")
public class OutboundOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantCode;

    /** 系统内部出库单号 */
    private String orderNo;

    /** 业务来源单号，例如销售单号/装车单号 */
    private String bizOrderNo;

    private String customerName;

    private Integer orderStatus;

    private Integer printStatus;

    private Long operatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

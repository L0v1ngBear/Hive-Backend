package my.hive_back.module.inventory.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 出库单主表 (用于归集客户的扫码出库明细，并供PC端打印)
 */
@Data
@TableName("outbound_order")
public class OutboundOrder {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO) // 如果你们系统主键是雪花算法，这里换成 IdType.ASSIGN_ID
    private Long id;

    /**
     * 租户编号
     */
    private String tenantCode;

    /**
     * 出库单号 (例如：CK-租户名-年月日-序列)
     */
    private String orderNo;

    /**
     * 客户名称/去向
     */
    private String customerName;

    /**
     * 打印状态：0-待打印，1-已打印，2-已作废
     */
    private Integer printStatus;

    /**
     * 操作人ID (首次扫码建单的人)
     */
    private Long operatorId;

    /**
     * 创建时间 (建单时间)
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间 (打印完成或状态变更时间)
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
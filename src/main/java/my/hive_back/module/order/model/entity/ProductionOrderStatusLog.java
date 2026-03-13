package my.hive_back.module.order.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 生产订单状态变更日志实体类
 */
@Data
@TableName("production_order_status_log")
public class ProductionOrderStatusLog {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_code")
    private String tenantCode;

    /**
     * 生产订单编号
     */
    @TableField("order_id")
    private String orderId;

    /**
     * 变更前状态
     */
    @TableField("old_status")
    private String oldStatus;

    /**
     * 变更后状态
     */
    @TableField("new_status")
    private String newStatus;

    /**
     * 操作类型：manual-手动，scan-扫码，auto-自动
     */
    @TableField("operate_type")
    private String operateType;

    /**
     * 变更备注
     */
    @TableField("remark")
    private String remark;

    /**
     * 操作人
     */
    @TableField("operator")
    private String operator;

    /**
     * 操作时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}
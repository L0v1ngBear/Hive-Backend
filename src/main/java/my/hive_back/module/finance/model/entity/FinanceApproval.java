package my.hive_back.module.finance.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * FinanceApproval 属于小程序后端财务模块，定义持久化实体结构，用于表字段映射。
 */
@Data
@TableName("finance_approval")
public class FinanceApproval {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("approval_code")
    private String approvalCode;

    @TableField("tenant_code")
    private String tenantCode;

    @TableField("apply_user_id")
    private Long applyUserId;

    @TableField("category")
    private String category;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("reason")
    private String reason;

    @TableField("attachment_url")
    private String attachmentUrl;

    /** 1-待审批，2-已通过，3-已拒绝 */
    @TableField("status")
    private Integer status;

    @TableField("auditor_id")
    private Long auditorId;

    @TableField("audit_comment")
    private String auditComment;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

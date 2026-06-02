package my.hive_back.module.approval.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("approval_default_auditor")
public class ApprovalDefaultAuditor {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantCode;

    private String approvalType;

    private Long auditorId;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

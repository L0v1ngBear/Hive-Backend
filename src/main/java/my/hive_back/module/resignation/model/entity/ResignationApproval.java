package my.hive_back.module.resignation.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工离职审批单。
 */
@Data
@TableName("employee_resignation_approval")
public class ResignationApproval {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String resignationCode;

    private String tenantCode;

    private Long applyUserId;

    private LocalDate expectedLeaveDate;

    private String reason;

    private String handoverNote;

    private Integer status;

    private Long auditorId;

    private String auditorIds;

    private String auditComment;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

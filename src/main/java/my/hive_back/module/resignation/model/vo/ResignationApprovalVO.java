package my.hive_back.module.resignation.model.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 离职审批展示对象。
 */
@Data
public class ResignationApprovalVO {

    private Long id;

    private String resignationCode;

    private Long applyUserId;

    private String applyUserName;

    private String applyDepartmentName;

    private LocalDate expectedLeaveDate;

    private String reason;

    private String handoverNote;

    private Integer status;

    private String statusText;

    private Long auditorId;

    private String auditorIds;

    private String auditorName;

    private String auditComment;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

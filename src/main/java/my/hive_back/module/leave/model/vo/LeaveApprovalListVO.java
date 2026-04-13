package my.hive_back.module.leave.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LeaveApprovalListVO {

    private Long id;

    private String leaveCode;

    private Integer leaveType;

    private String leaveTypeText;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String reason;

    private Integer status;

    private String statusText;

    private String auditComment;

    private Long applyUserId;

    private String applyUserName;

    private String applyDepartmentName;

    private Long auditorId;

    private String auditorName;

    private LocalDateTime createTime;
}
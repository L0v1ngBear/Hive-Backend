package my.hive_back.module.leave.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LeaveDetailVO {
    private String leaveCode;     // 请假编码
    private String applyUserName; // 申请人姓名
    private Integer leaveType;    // 请假类型
    private LocalDateTime startTime; // 开始时间
    private LocalDateTime endTime;   // 结束时间
    private String reason;        // 请假事由
    private Integer status;       // 审批状态 (0-待审批, 1-已同意, 2-已拒绝)
    private String auditComment;  // 审批意见
    private String auditorName;   // 当前审批人/处理人姓名
}

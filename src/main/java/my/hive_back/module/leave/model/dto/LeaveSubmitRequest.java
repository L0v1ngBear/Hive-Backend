package my.hive_back.module.leave.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LeaveSubmitRequest {

    @NotNull(message = "请假类型不能为空")
    private Integer leaveType;

    @NotNull(message = "请假开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "请假结束时间不能为空")
    private LocalDateTime endTime;

    @NotBlank(message = "请假事由不能为空")
    private String reason;

    /**
     * 可手动指定审批人；为空时走当前租户的默认审批负责人。
     */
    private Long auditorId;
}

package my.hive_back.module.leave.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LeaveSubmitRequest {
    /**
     * 请假类型：1-事假，2-病假，3-年假，4-调休等
     */
    @NotBlank
    private Integer leaveType;

    /**
     * 请假开始时间
     */
    @NotBlank
    private LocalDateTime startTime;

    /**
     * 请假结束时间
     */
    @NotBlank
    private LocalDateTime endTime;

    /**
     * 请假事由
     */
    @NotBlank
    private String reason;
}
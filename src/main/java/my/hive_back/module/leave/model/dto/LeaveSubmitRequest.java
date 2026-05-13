package my.hive_back.module.leave.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
/**
 * LeaveSubmitRequest 属于小程序后端请假模块，定义入参结构。
 */
@Data
public class LeaveSubmitRequest {
    /**
     * 请假类型：1-事假，2-病假，3-年假，4-调休等
     */
    @NotNull
    private Integer leaveType;

    /**
     * 请假开始时间
     */
    @NotNull
    private LocalDateTime startTime;

    /**
     * 请假结束时间
     */
    @NotNull
    private LocalDateTime endTime;

    /**
     * 请假事由
     */
    @NotBlank
    private String reason;
}

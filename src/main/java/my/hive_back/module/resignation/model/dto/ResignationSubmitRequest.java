package my.hive_back.module.resignation.model.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ResignationSubmitRequest {

    @NotNull(message = "预计离职日期不能为空")
    @FutureOrPresent(message = "预计离职日期不能早于今天")
    private LocalDate expectedLeaveDate;

    @NotBlank(message = "离职原因不能为空")
    @Size(max = 500, message = "离职原因不能超过500字")
    private String reason;

    /**
     * 可手动指定审批人；为空时走当前租户的默认审批负责人。
     */
    private Long auditorId;

    @Size(max = 500, message = "交接说明不能超过500字")
    private String handoverNote;
}

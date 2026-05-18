package my.hive_back.module.resignation.model.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 离职审批提交参数。
 */
@Data
public class ResignationSubmitRequest {

    @NotNull(message = "预计离职日期不能为空")
    @FutureOrPresent(message = "预计离职日期不能早于今天")
    private LocalDate expectedLeaveDate;

    @NotBlank(message = "离职原因不能为空")
    @Size(max = 500, message = "离职原因不能超过500字")
    private String reason;

    @Size(max = 500, message = "交接说明不能超过500字")
    private String handoverNote;
}

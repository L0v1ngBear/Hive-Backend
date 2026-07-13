package my.hive_back.module.approval.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Quality process audit request.
 */
@Data
public class QualityAuditRequest {

    @NotBlank(message = "质量编号不能为空")
    private String defectiveId;

    @NotNull(message = "审批动作不能为空")
    private Integer action;

    @Size(max = 300, message = "审批意见不能超过300字")
    private String comment;
}

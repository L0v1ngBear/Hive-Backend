package my.hive_back.module.resignation.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 离职审批处理参数。
 */
@Data
public class ResignationAuditRequest {

    @NotBlank(message = "离职审批单号不能为空")
    private String resignationCode;

    /** 1-同意，2-拒绝 */
    @NotNull(message = "审批动作不能为空")
    private Integer action;

    @Size(max = 500, message = "审批意见不能超过500字")
    private String comment;
}

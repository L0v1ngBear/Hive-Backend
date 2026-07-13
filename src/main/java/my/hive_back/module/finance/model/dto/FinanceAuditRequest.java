package my.hive_back.module.finance.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
/**
 * FinanceAuditRequest 属于小程序后端财务模块，定义入参结构。
 */
@Data
public class FinanceAuditRequest {

    @NotBlank(message = "审批单号不能为空")
    private String approvalCode;

    /** 1-同意，2-拒绝 */
    @NotNull(message = "审批动作不能为空")
    private Integer action;

    private String comment;
}

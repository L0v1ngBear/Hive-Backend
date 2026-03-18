package my.hive_back.module.leave.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuditRequest {

    /**
     * 审批单主键 ID
     */
    @NotBlank
    private String leaveCode;

    /**
     * 审批动作：
     * 1 - 同意
     * 2 - 拒绝
     */
    @NotBlank
    private Integer action;

    /**
     * 审批意见 (可选填，如果是拒绝建议必填)
     * 例如："同意，注意交接工作" 或 "项目太忙，不批"
     */
    private String comment;
}

package my.hive_back.module.approval.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Confirm a pending order from approval center.
 */
@Data
public class OrderApprovalAuditRequest {

    @NotBlank(message = "订单类型不能为空")
    private String orderType;

    @NotBlank(message = "订单号不能为空")
    private String orderId;

    @NotNull(message = "审批动作不能为空")
    private Integer action;

    @Size(max = 300, message = "审批意见不能超过300字")
    private String comment;
}

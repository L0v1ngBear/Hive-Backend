package my.hive_back.module.badproduct.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
/**
 * BadProductSaveRequest 属于小程序后端坏品模块，定义入参结构。
 */
@Data
public class BadProductSaveRequest {

    private String defectiveId;

    private String orderId;

    @NotBlank(message = "质量类型不能为空")
    private String type;

    @NotNull(message = "数量不能为空")
    @DecimalMin(value = "0.0", inclusive = false, message = "数量必须大于0")
    private BigDecimal quantity;

    @NotNull(message = "损失金额不能为空")
    @DecimalMin(value = "0.0", inclusive = false, message = "损失金额必须大于0")
    private BigDecimal lossAmount;

    private String description;

    /**
     * 负责跟进本次质量问题的人员。
     */
    private String responsiblePerson;

    /**
     * 本次质量异常的即时处理措施。
     */
    private String processMeasure;

    /**
     * 针对同类问题的后续改进方案。
     */
    private String improvementPlan;

    private String attachmentName;

    private String attachmentUrl;

    private Long attachmentSize;
}

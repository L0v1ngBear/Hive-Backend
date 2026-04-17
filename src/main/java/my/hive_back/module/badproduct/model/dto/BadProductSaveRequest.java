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

    @NotBlank(message = "次品类型不能为空")
    private String type;

    @NotNull(message = "数量不能为空")
    @DecimalMin(value = "0.0", inclusive = false, message = "数量必须大于0")
    private BigDecimal quantity;

    @NotNull(message = "损失金额不能为空")
    @DecimalMin(value = "0.0", inclusive = false, message = "损失金额必须大于0")
    private BigDecimal lossAmount;

    private String description;
}

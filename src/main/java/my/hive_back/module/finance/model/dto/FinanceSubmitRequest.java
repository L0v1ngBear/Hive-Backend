package my.hive_back.module.finance.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
/**
 * FinanceSubmitRequest 属于小程序后端财务模块，定义入参结构。
 */
@Data
public class FinanceSubmitRequest {

    @NotBlank(message = "财务类别不能为空")
    private String category;

    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于0")
    private BigDecimal amount;

    @NotBlank(message = "申请事由不能为空")
    private String reason;

    private String attachmentName;

    private String attachmentUrl;

    private Long attachmentSize;
}

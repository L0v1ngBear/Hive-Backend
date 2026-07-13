package my.hive_back.module.order.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * ProductionOrderAddRequest 属于小程序后端订单模块，定义入参结构。
 */
@Data
@Schema(description = "创建生产订单请求")
public class ProductionOrderAddRequest {

    @NotBlank(message = "面料型号不能为空")
    @Schema(description = "面料型号", example = "T800-210")
    private String modelCode;

    @NotNull(message = "克重不能为空")
    @DecimalMin(value = "0.0", inclusive = false, message = "克重必须大于0")
    @Schema(description = "克重")
    private Float weight;

    @NotNull(message = "规格不能为空")
    @Positive(message = "规格必须为正数")
    @Schema(description = "规格")
    private Float spec;

    @NotNull(message = "订单数量不能为空")
    @Min(value = 1, message = "订单数量至少为1")
    @Schema(description = "数量")
    private Integer quantity;

    @Schema(description = "客户名称")
    private String customerName;

    @Schema(description = "项目名称")
    private String projectName;

    @Schema(description = "品牌名称")
    private String brandName;

    @Schema(description = "订单小项类型：sample_room-样板间，bulk-大货，replenishment-补单")
    private String orderCategory;

    @NotBlank(message = "信息渠道不能为空")
    @Schema(description = "信息渠道")
    private String informationChannel;

}

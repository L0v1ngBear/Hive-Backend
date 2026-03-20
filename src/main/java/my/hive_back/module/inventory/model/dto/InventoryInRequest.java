package my.hive_back.module.inventory.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InventoryInRequest {

    private String barcode;

    @NotBlank
    private String modelCode;

    @NotNull
    @DecimalMin(value = "0.0", message = "米数不能小于等于0")
    private Float meters;

    @NotNull
    @DecimalMin(value = "0.0", message = "规格不能小于等于0")
    private Float spec;

    // 入库类型
    @NotBlank
    private String inType;
}

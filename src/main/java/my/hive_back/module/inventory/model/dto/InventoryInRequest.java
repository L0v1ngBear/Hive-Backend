package my.hive_back.module.inventory.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class InventoryInRequest {

    private String barcode;

    @NotBlank
    private String modelCode;

    @NotNull
    @Positive(message = "米数不能小于等于0")
    private Float meters;

    @NotNull
    @Positive(message = "规格不能小于等于0")
    private Float spec;

    // 入库类型
    @NotBlank
    private String inType;
}

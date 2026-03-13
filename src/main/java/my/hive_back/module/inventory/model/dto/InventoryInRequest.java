package my.hive_back.module.inventory.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InventoryInRequest {

    private String barcode;

    @NotBlank
    private String modelCode;

    @NotBlank
    @Min(value = 1, message = "米数不能小于等于0")
    private Float meters;

    @NotBlank
    private String width;

    // 入库类型
    @NotBlank
    private String inType;
}

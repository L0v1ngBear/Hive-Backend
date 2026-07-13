package my.hive_back.module.inventory.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
/**
 * InventoryOutRequest 属于小程序后端库存模块，定义入参结构。
 */
@Data
public class InventoryOutRequest {

    @NotBlank
    private String barcode;

    @NotNull
    private Float meters;

    @NotBlank
    private String orderNo;

    @NotBlank
    private String customerName;

    private String requestId;
}

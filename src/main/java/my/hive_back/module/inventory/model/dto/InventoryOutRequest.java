package my.hive_back.module.inventory.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InventoryOutRequest {

    @NotBlank
    private String barcode;


    private Float meters;
}

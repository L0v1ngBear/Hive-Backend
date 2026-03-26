package my.hive_back.module.inventory.model.vo;

import lombok.Data;

@Data
public class ClothInfoVO {

    private String barcode;

    private String modelCode;

    private Float spec;

    private Float meters;
}

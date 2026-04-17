package my.hive_back.module.inventory.model.vo;

import lombok.Data;
/**
 * ClothInfoVO 属于小程序后端库存模块，定义出参结构。
 */
@Data
public class ClothInfoVO {

    private String barcode;

    private String modelCode;

    private Float spec;

    private Float meters;
}

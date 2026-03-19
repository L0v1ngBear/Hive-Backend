package my.hive_back.module.inventory.model.vo;

import lombok.Data;

@Data
public class BarCodeSearchVO {

    private String barcode;           // 条码
    private String modelCode;      // 物料名称
    private Float spec;              // 规格
    private Float meters;           // 米数
    private Float remainingMeters;           // 剩余库存
    private Integer isBad;
}

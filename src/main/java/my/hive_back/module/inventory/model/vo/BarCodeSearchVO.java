package my.hive_back.module.inventory.model.vo;

import lombok.Data;
/**
 * BarCodeSearchVO 属于小程序后端库存模块，定义出参结构。
 */
@Data
public class BarCodeSearchVO {

    private String barcode;           // 条码
    private String modelCode;      // 物料名称
    private Float spec;              // 规格
    private Float meters;           // 米数
    private Float remainingMeters;           // 剩余库存
    private Integer isBad;
}

package my.hive_back.module.inventory.model.vo;

public class BarCodeSearchVO {

    private String barcode;           // 条码
    private String materialName;      // 物料名称
    private String spec;              // 规格
    private String batchNo;           // 批次号
    private int stockCount;           // 剩余库存
    private String orderNo;           // 所属订单
    private boolean canOutbound;      // 是否可出库
}

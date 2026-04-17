package my.hive_back.module.inventory;

import lombok.Getter;
/**
 * InventoryOperateTypeEnum 属于小程序后端库存模块，属于该领域的细分实现。
 */
@Getter

public enum InventoryOperateTypeEnum {

    /**
     * 入库
     */
    IN(0, "入库"),
    /**
     * 出库
     */
    OUT(1, "出库"),

    /**
     * 部分出库
     */
    PART_OUT(2, "部分出库");

    @Getter
    private int code;
    private String desc;

     InventoryOperateTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

}

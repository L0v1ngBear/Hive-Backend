package my.hive_back.module.inventory;

import lombok.Getter;

public enum InventoryOperateTypeEnum {
    /**
     * 入库
     */
    IN(1, "入库"),
    /**
     * 出库
     */
    OUT(2, "出库");

    @Getter
    private int code;
    private String desc;

     InventoryOperateTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

}

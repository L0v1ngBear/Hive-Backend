package my.hive_back.module.inventory;

import lombok.Getter;

@Getter
public enum InventoryRecordOperateTypeEnum {

    IN(0, "入库"),
    OUT(1, "出库"),
    EXTERNAL_IMPORT(2, "外部导入");

    private final int code;
    private final String desc;

    InventoryRecordOperateTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}

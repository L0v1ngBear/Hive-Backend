package my.hive_back.module.inventory;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public enum InventoryInTypeEnum {

    SCAN("scan", "扫码入库"),
    HAND("hand", "手动入库"),
    AUTO("auto", "检验机自动入库");

    private final String code;
    private final String desc;

    InventoryInTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static InventoryInTypeEnum getCode(@NotBlank String inType) {
        for (InventoryInTypeEnum inTypeEnum : InventoryInTypeEnum.values()) {
            if (inTypeEnum.getCode().equals(inType)) {
                return inTypeEnum;
            }
        }
        throw new IllegalArgumentException("未知的入库类型");
    }
}

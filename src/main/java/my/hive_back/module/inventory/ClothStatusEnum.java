package my.hive_back.module.inventory;

import lombok.Getter;

@Getter
public enum ClothStatusEnum {
    /**
     * 在库
     */
    IN_STOCK(0),

    /**
     * 已出库
     */
    OUT_STOCK(1),

    /**
     * 部分出库
     */
    PARTIAL_OUT_STOCK(2);

    private final Integer value;

    ClothStatusEnum(Integer value) {
        this.value = value;
    }
}

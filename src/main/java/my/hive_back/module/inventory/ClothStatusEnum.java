package my.hive_back.module.inventory;

import lombok.Getter;
/**
 * ClothStatusEnum 属于小程序后端库存模块，属于该领域的细分实现。
 */
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

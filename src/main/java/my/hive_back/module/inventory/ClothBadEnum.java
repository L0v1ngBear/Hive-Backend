package my.hive_back.module.inventory;

import lombok.Getter;
/**
 * ClothBadEnum 属于小程序后端库存模块，属于该领域的细分实现。
 */
@Getter
public enum ClothBadEnum {

    IS_GOOD(0, "良品"),
    IS_BAD(1, "次品");

    private final Integer value;
    private final String description;

    ClothBadEnum(Integer value, String description) {
        this.value = value;
        this.description = description;
    }
}

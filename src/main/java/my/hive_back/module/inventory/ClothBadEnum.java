package my.hive_back.module.inventory;

import lombok.Getter;

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

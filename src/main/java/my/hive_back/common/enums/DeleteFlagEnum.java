package my.hive_back.common.enums;

import lombok.Getter;

@Getter
public enum DeleteFlagEnum {

    NORMAL(0),
    DELETED(1);

    private final Integer code;

    DeleteFlagEnum(Integer code) {
        this.code = code;
    }

    public boolean matches(Integer value) {
        return code.equals(value);
    }
}

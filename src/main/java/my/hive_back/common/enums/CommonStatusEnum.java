package my.hive_back.common.enums;

import lombok.Getter;

@Getter
public enum CommonStatusEnum {

    DISABLED(0),
    ENABLED(1);

    private final Integer code;

    CommonStatusEnum(Integer code) {
        this.code = code;
    }

    public boolean matches(Integer value) {
        return code.equals(value);
    }
}

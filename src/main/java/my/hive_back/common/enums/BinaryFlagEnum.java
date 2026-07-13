package my.hive_back.common.enums;

import lombok.Getter;

@Getter
public enum BinaryFlagEnum {

    NO(0),
    YES(1);

    private final Integer code;

    BinaryFlagEnum(Integer code) {
        this.code = code;
    }

    public boolean matches(Integer value) {
        return code.equals(value);
    }

    public static Integer codeOf(Boolean value) {
        return Boolean.TRUE.equals(value) ? YES.code : NO.code;
    }
}

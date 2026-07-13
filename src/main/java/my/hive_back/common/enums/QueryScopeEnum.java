package my.hive_back.common.enums;

import lombok.Getter;

@Getter
public enum QueryScopeEnum {

    ALL("all"),
    PENDING("pending");

    public static final String CODE_ALL = "all";
    public static final String CODE_PENDING = "pending";

    private final String code;

    QueryScopeEnum(String code) {
        this.code = code;
    }

    public boolean matches(String value) {
        return value != null && code.equalsIgnoreCase(value.trim());
    }
}

package my.hive_back.module.tenant;

import lombok.Getter;

@Getter
public enum TenantPlanEnum {

    TRIAL("TRIAL"),
    PROFESSIONAL("PROFESSIONAL"),
    PRIVATE("PRIVATE");

    private final String code;

    TenantPlanEnum(String code) {
        this.code = code;
    }

    public boolean matches(String value) {
        return value != null && code.equalsIgnoreCase(value.trim());
    }
}

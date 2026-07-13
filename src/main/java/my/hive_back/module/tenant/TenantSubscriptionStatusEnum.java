package my.hive_back.module.tenant;

import lombok.Getter;

@Getter
public enum TenantSubscriptionStatusEnum {

    EXPIRED("EXPIRED"),
    SUSPENDED("SUSPENDED");

    private final String code;

    TenantSubscriptionStatusEnum(String code) {
        this.code = code;
    }

    public boolean matches(String value) {
        return value != null && code.equalsIgnoreCase(value.trim());
    }
}

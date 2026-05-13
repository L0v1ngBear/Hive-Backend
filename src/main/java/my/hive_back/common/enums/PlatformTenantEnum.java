package my.hive_back.common.enums;

import lombok.Getter;

@Getter
public enum PlatformTenantEnum {

    SUPER("super");

    private final String code;

    PlatformTenantEnum(String code) {
        this.code = code;
    }

    public boolean matches(String tenantCode) {
        return tenantCode != null && code.equalsIgnoreCase(tenantCode.trim());
    }
}

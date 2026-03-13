package my.hive_back.module.tenant;

public enum TenantStatusEnum {
    DISABLE(0, "禁用"),
    NORMAL(1, "正常"),
    FREEZE(2, "冻结");

    private final Integer code;
    private final String desc;

    // 构造器+getter
    TenantStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}

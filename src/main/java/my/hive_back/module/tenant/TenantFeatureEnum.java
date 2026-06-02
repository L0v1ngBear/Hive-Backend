package my.hive_back.module.tenant;

import lombok.Getter;

@Getter
public enum TenantFeatureEnum {

    MODULE_DASHBOARD("module.dashboard"),
    MODULE_ORDER("module.order"),
    MODULE_INVENTORY("module.inventory"),
    MODULE_BAD_PRODUCT("module.badProduct"),
    MODULE_CUSTOMER("module.customer"),
    MODULE_PRICE("module.price"),
    MODULE_RECEIPT("module.receipt"),
    MODULE_APPROVAL("module.approval"),
    MODULE_ATTENDANCE("module.attendance"),
    MODULE_EMPLOYEE("module.employee"),
    MODULE_EQUIPMENT("module.equipment"),
    MODULE_ROLE("module.role"),
    MODULE_LABEL("module.label"),
    MODULE_DOCUMENT("module.document"),
    MODULE_MANUAL("module.manual"),
    AI_ADVICE("aiAdvice"),
    ADVANCED_AI("advancedAi");

    public static final String CODE_MODULE_DASHBOARD = "module.dashboard";
    public static final String CODE_MODULE_ORDER = "module.order";
    public static final String CODE_MODULE_INVENTORY = "module.inventory";
    public static final String CODE_MODULE_BAD_PRODUCT = "module.badProduct";
    public static final String CODE_MODULE_CUSTOMER = "module.customer";
    public static final String CODE_MODULE_PRICE = "module.price";
    public static final String CODE_MODULE_RECEIPT = "module.receipt";
    public static final String CODE_MODULE_APPROVAL = "module.approval";
    public static final String CODE_MODULE_ATTENDANCE = "module.attendance";
    public static final String CODE_MODULE_EMPLOYEE = "module.employee";
    public static final String CODE_MODULE_EQUIPMENT = "module.equipment";
    public static final String CODE_MODULE_ROLE = "module.role";
    public static final String CODE_MODULE_LABEL = "module.label";
    public static final String CODE_MODULE_DOCUMENT = "module.document";
    public static final String CODE_MODULE_MANUAL = "module.manual";
    public static final String CODE_AI_ADVICE = "aiAdvice";
    public static final String CODE_ADVANCED_AI = "advancedAi";

    private final String code;

    TenantFeatureEnum(String code) {
        this.code = code;
    }
}

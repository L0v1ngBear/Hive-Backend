package my.hive_back.module.sys.model.enums;

import lombok.Getter;

@Getter
public enum PermissionCodeEnum {

    ALL("*"),
    APPROVAL_FINANCE("approval:finance"),
    APPROVAL_FINANCE_AUDIT("approval:finance:audit"),
    APPROVAL_FINANCE_DETAIL("approval:finance:detail"),
    APPROVAL_FINANCE_SUBMIT("approval:finance:submit"),
    APPROVAL_LEAVE("approval:leave"),
    APPROVAL_LEAVE_AUDIT("approval:leave:audit"),
    APPROVAL_LEAVE_DETAIL("approval:leave:detail"),
    APPROVAL_LEAVE_SUBMIT("approval:leave:submit"),
    ATTENDANCE_ALL("attendance:*"),
    ATTENDANCE_PUNCH("attendance:punch"),
    ATTENDANCE_RECORD_LIST("attendance:record:list"),
    BADPRODUCT_ALL("badproduct:*"),
    BADPRODUCT_LIST("badproduct:list"),
    BADPRODUCT_PROCESS("badproduct:process"),
    BADPRODUCT_SAVE("badproduct:save"),
    CUSTOMER_ADD("customer:add"),
    CUSTOMER_DETAIL("customer:detail"),
    CUSTOMER_PAGE("customer:page"),
    DOCUMENT_BREADCRUMBS("document:breadcrumbs"),
    DOCUMENT_FILE_UPLOAD("document:file:upload"),
    DOCUMENT_FOLDER_CREATE("document:folder:create"),
    DOCUMENT_LIST("document:list"),
    DOCUMENT_MOVE("document:move"),
    DOCUMENT_RENAME("document:rename"),
    INVENTORY("inventory"),
    INVENTORY_ALL("inventory:*"),
    INVENTORY_BARCODE_SEARCH("inventory:barcode:search"),
    INVENTORY_CLOTH_IN("inventory:cloth:in"),
    INVENTORY_CLOTH_OUT("inventory:cloth:out"),
    INVENTORY_MODEL_SEARCH("inventory:model:search"),
    INVENTORY_RECORD_RECENT("inventory:record:recent"),
    INVENTORY_TREND("inventory:trend"),
    INVENTORY_WARNING_LIST("inventory:warning:list"),
    LABEL_TEMPLATE_DEFAULT("label:template:default"),
    LABEL_TEMPLATE_DETAIL("label:template:detail"),
    LABEL_TEMPLATE_DISABLE("label:template:disable"),
    LABEL_TEMPLATE_LIST("label:template:list"),
    LABEL_TEMPLATE_SAVE("label:template:save"),
    LABEL_TEMPLATE_UPLOAD("label:template:upload"),
    PRODUCTION_ORDER_ALL("production:order:*"),
    PRODUCTION_ORDER_DETAIL("production:order:detail"),
    PRODUCTION_ORDER_LIST("production:order:list"),
    PRODUCTION_ORDER_LOG("production:order:log"),
    PRODUCTION_ORDER_STATUS("production:order:status"),
    SALES_ORDER_ALL("sales:order:*"),
    SALES_ORDER_DETAIL("sales:order:detail"),
    SALES_ORDER_LIST("sales:order:list"),
    SALES_ORDER_STATUS("sales:order:status");

    public static final String CODE_ALL = "*";
    public static final String CODE_APPROVAL_FINANCE = "approval:finance";
    public static final String CODE_APPROVAL_FINANCE_AUDIT = "approval:finance:audit";
    public static final String CODE_APPROVAL_FINANCE_DETAIL = "approval:finance:detail";
    public static final String CODE_APPROVAL_FINANCE_SUBMIT = "approval:finance:submit";
    public static final String CODE_APPROVAL_LEAVE = "approval:leave";
    public static final String CODE_APPROVAL_LEAVE_AUDIT = "approval:leave:audit";
    public static final String CODE_APPROVAL_LEAVE_DETAIL = "approval:leave:detail";
    public static final String CODE_APPROVAL_LEAVE_SUBMIT = "approval:leave:submit";
    public static final String CODE_ATTENDANCE_ALL = "attendance:*";
    public static final String CODE_ATTENDANCE_PUNCH = "attendance:punch";
    public static final String CODE_ATTENDANCE_RECORD_LIST = "attendance:record:list";
    public static final String CODE_BADPRODUCT_ALL = "badproduct:*";
    public static final String CODE_BADPRODUCT_LIST = "badproduct:list";
    public static final String CODE_BADPRODUCT_PROCESS = "badproduct:process";
    public static final String CODE_BADPRODUCT_SAVE = "badproduct:save";
    public static final String CODE_CUSTOMER_ADD = "customer:add";
    public static final String CODE_CUSTOMER_DETAIL = "customer:detail";
    public static final String CODE_CUSTOMER_PAGE = "customer:page";
    public static final String CODE_DOCUMENT_BREADCRUMBS = "document:breadcrumbs";
    public static final String CODE_DOCUMENT_FILE_UPLOAD = "document:file:upload";
    public static final String CODE_DOCUMENT_FOLDER_CREATE = "document:folder:create";
    public static final String CODE_DOCUMENT_LIST = "document:list";
    public static final String CODE_DOCUMENT_MOVE = "document:move";
    public static final String CODE_DOCUMENT_RENAME = "document:rename";
    public static final String CODE_INVENTORY = "inventory";
    public static final String CODE_INVENTORY_ALL = "inventory:*";
    public static final String CODE_INVENTORY_BARCODE_SEARCH = "inventory:barcode:search";
    public static final String CODE_INVENTORY_CLOTH_IN = "inventory:cloth:in";
    public static final String CODE_INVENTORY_CLOTH_OUT = "inventory:cloth:out";
    public static final String CODE_INVENTORY_MODEL_SEARCH = "inventory:model:search";
    public static final String CODE_INVENTORY_RECORD_RECENT = "inventory:record:recent";
    public static final String CODE_INVENTORY_TREND = "inventory:trend";
    public static final String CODE_INVENTORY_WARNING_LIST = "inventory:warning:list";
    public static final String CODE_LABEL_TEMPLATE_DEFAULT = "label:template:default";
    public static final String CODE_LABEL_TEMPLATE_DETAIL = "label:template:detail";
    public static final String CODE_LABEL_TEMPLATE_DISABLE = "label:template:disable";
    public static final String CODE_LABEL_TEMPLATE_LIST = "label:template:list";
    public static final String CODE_LABEL_TEMPLATE_SAVE = "label:template:save";
    public static final String CODE_LABEL_TEMPLATE_UPLOAD = "label:template:upload";
    public static final String CODE_PRODUCTION_ORDER_ALL = "production:order:*";
    public static final String CODE_PRODUCTION_ORDER_DETAIL = "production:order:detail";
    public static final String CODE_PRODUCTION_ORDER_LIST = "production:order:list";
    public static final String CODE_PRODUCTION_ORDER_LOG = "production:order:log";
    public static final String CODE_PRODUCTION_ORDER_STATUS = "production:order:status";
    public static final String CODE_SALES_ORDER_ALL = "sales:order:*";
    public static final String CODE_SALES_ORDER_DETAIL = "sales:order:detail";
    public static final String CODE_SALES_ORDER_LIST = "sales:order:list";
    public static final String CODE_SALES_ORDER_STATUS = "sales:order:status";

    private final String code;

    PermissionCodeEnum(String code) {
        this.code = code;
    }
}

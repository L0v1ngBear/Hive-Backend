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
    APPROVAL_RESIGNATION("approval:resignation"),
    APPROVAL_RESIGNATION_AUDIT("approval:resignation:audit"),
    APPROVAL_RESIGNATION_DETAIL("approval:resignation:detail"),
    APPROVAL_RESIGNATION_SUBMIT("approval:resignation:submit"),
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
    CUSTOMER_UPDATE("customer:update"),
    DOCUMENT_BREADCRUMBS("document:breadcrumbs"),
    DOCUMENT_FILE_UPLOAD("document:file:upload"),
    DOCUMENT_FOLDER_CREATE("document:folder:create"),
    DOCUMENT_LIST("document:list"),
    DOCUMENT_MOVE("document:move"),
    DOCUMENT_RENAME("document:rename"),
    EQUIPMENT_INSPECTION_SUBMIT("equipment:inspection:submit"),
    EQUIPMENT_LIST("equipment:list"),
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
    ORDER_ALL("order:*"),
    ORDER_CREATE("order:create"),
    ORDER_DETAIL("order:detail"),
    ORDER_LIST("order:list"),
    ORDER_STATUS_BUDGET_COMPLETED("order:status:budget-completed"),
    ORDER_STATUS_BUDGETING("order:status:budgeting"),
    ORDER_STATUS_CANCELLED("order:status:cancelled"),
    ORDER_STATUS_COMPLETED("order:status:completed"),
    ORDER_STATUS_PENDING_CANCEL("order:status:pending-cancel"),
    ORDER_STATUS_PENDING_CONFIRM("order:status:pending-confirm"),
    ORDER_STATUS_PENDING_MATERIAL("order:status:pending-material"),
    ORDER_STATUS_PENDING_PAY("order:status:pending-pay"),
    ORDER_STATUS_PENDING_SHIP("order:status:pending-ship"),
    ORDER_STATUS_PRODUCING("order:status:producing"),
    ORDER_STATUS_SHIPPED("order:status:shipped");

    public static final String CODE_ALL = "*";
    public static final String CODE_APPROVAL_FINANCE = "approval:finance";
    public static final String CODE_APPROVAL_FINANCE_AUDIT = "approval:finance:audit";
    public static final String CODE_APPROVAL_FINANCE_DETAIL = "approval:finance:detail";
    public static final String CODE_APPROVAL_FINANCE_SUBMIT = "approval:finance:submit";
    public static final String CODE_APPROVAL_LEAVE = "approval:leave";
    public static final String CODE_APPROVAL_LEAVE_AUDIT = "approval:leave:audit";
    public static final String CODE_APPROVAL_LEAVE_DETAIL = "approval:leave:detail";
    public static final String CODE_APPROVAL_LEAVE_SUBMIT = "approval:leave:submit";
    public static final String CODE_APPROVAL_RESIGNATION = "approval:resignation";
    public static final String CODE_APPROVAL_RESIGNATION_AUDIT = "approval:resignation:audit";
    public static final String CODE_APPROVAL_RESIGNATION_DETAIL = "approval:resignation:detail";
    public static final String CODE_APPROVAL_RESIGNATION_SUBMIT = "approval:resignation:submit";
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
    public static final String CODE_CUSTOMER_UPDATE = "customer:update";
    public static final String CODE_DOCUMENT_BREADCRUMBS = "document:breadcrumbs";
    public static final String CODE_DOCUMENT_FILE_UPLOAD = "document:file:upload";
    public static final String CODE_DOCUMENT_FOLDER_CREATE = "document:folder:create";
    public static final String CODE_DOCUMENT_LIST = "document:list";
    public static final String CODE_DOCUMENT_MOVE = "document:move";
    public static final String CODE_DOCUMENT_RENAME = "document:rename";
    public static final String CODE_EQUIPMENT_INSPECTION_SUBMIT = "equipment:inspection:submit";
    public static final String CODE_EQUIPMENT_LIST = "equipment:list";
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
    public static final String CODE_ORDER_ALL = "order:*";
    public static final String CODE_ORDER_CREATE = "order:create";
    public static final String CODE_ORDER_DETAIL = "order:detail";
    public static final String CODE_ORDER_LIST = "order:list";
    public static final String CODE_ORDER_STATUS_ALL = "order:status:*";
    public static final String CODE_ORDER_STATUS_PREFIX = "order:status:";
    public static final String CODE_ORDER_STATUS_BUDGET_COMPLETED = "order:status:budget-completed";
    public static final String CODE_ORDER_STATUS_BUDGETING = "order:status:budgeting";
    public static final String CODE_ORDER_STATUS_CANCELLED = "order:status:cancelled";
    public static final String CODE_ORDER_STATUS_COMPLETED = "order:status:completed";
    public static final String CODE_ORDER_STATUS_PENDING_CANCEL = "order:status:pending-cancel";
    public static final String CODE_ORDER_STATUS_PENDING_CONFIRM = "order:status:pending-confirm";
    public static final String CODE_ORDER_STATUS_PENDING_MATERIAL = "order:status:pending-material";
    public static final String CODE_ORDER_STATUS_PENDING_PAY = "order:status:pending-pay";
    public static final String CODE_ORDER_STATUS_PENDING_SHIP = "order:status:pending-ship";
    public static final String CODE_ORDER_STATUS_PRODUCING = "order:status:producing";
    public static final String CODE_ORDER_STATUS_SHIPPED = "order:status:shipped";

    private final String code;

    PermissionCodeEnum(String code) {
        this.code = code;
    }
}

package my.hive_back.module.order;

import lombok.Getter;

/**
 * 小程序后端订单状态枚举。
 */
@Getter
public enum OrderStatusEnum {

    PENDING_CONFIRM("pending_confirm", 0, "待确认"),
    PENDING_PAY("pending_pay", 1, "待收款"),
    PENDING_MATERIAL("pending_material", 2, "备料中"),
    PRODUCING("producing", 3, "生产中"),
    PENDING_SHIP("pending_ship", 4, "待发货"),
    SHIPPED("shipped", 5, "已发货"),
    COMPLETED("completed", 6, "已完成"),
    BUDGETING("budgeting", 20, "预算中"),
    BUDGET_COMPLETED("budget_completed", 21, "预算完成"),
    PENDING_CANCEL("pending_cancel", 98, "取消审核中"),
    CANCELLED("cancelled", 99, "已取消");

    private final String code;
    private final Integer index;
    private final String name;

    OrderStatusEnum(String code, Integer index, String name) {
        this.code = code;
        this.index = index;
        this.name = name;
    }

    public static OrderStatusEnum getByCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (OrderStatusEnum status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的订单状态编码：" + code);
    }

    public static OrderStatusEnum getByIndex(Integer index) {
        if (index == null) {
            return null;
        }
        for (OrderStatusEnum status : values()) {
            if (status.getIndex().equals(index)) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的订单状态索引：" + index);
    }

    public boolean canFlowTo(OrderStatusEnum targetStatus) {
        if (targetStatus == null) {
            return false;
        }
        return targetStatus.getIndex() > this.getIndex();
    }

    public static boolean canFlowTo(String currentStatusCode, String targetStatusCode) {
        OrderStatusEnum currentStatus = getByCode(currentStatusCode);
        OrderStatusEnum targetStatus = getByCode(targetStatusCode);
        return currentStatus != null && currentStatus.canFlowTo(targetStatus);
    }
}

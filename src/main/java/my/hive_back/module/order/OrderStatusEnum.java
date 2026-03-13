package my.hive_back.module.order;

import lombok.Getter;

import lombok.Getter;

/**
 * 生产订单状态枚举
 * 对应 status（字符串）和 statusIndex（数字索引）
 */
@Getter
public enum OrderStatusEnum {

    /**
     * 待确认
     */
    PENDING_CONFIRM("pending_confirm", 0, "待确认"),

    /**
     * 备料中
     */
    PENDING_MATERIAL("pending_material", 1, "备料中"),

    /**
     * 生产中
     */
    PRODUCING("producing", 2, "生产中"),

    /**
     * 待发货
     */
    PENDING_SHIP("pending_ship", 3, "待发货"),

    /**
     * 已发货
     */
    SHIPPED("shipped", 4, "已发货"),

    /**
     * 已完成
     */
    COMPLETED("completed", 5, "已完成");

    /**
     * 状态编码（对应数据库status字段）
     */
    private final String code;

    /**
     * 状态索引（对应数据库status_index字段）
     */
    private final Integer index;

    /**
     * 状态名称（前端展示用）
     */
    private final String name;

    // 构造方法
    OrderStatusEnum(String code, Integer index, String name) {
        this.code = code;
        this.index = index;
        this.name = name;
    }

    /**
     * 根据状态编码获取枚举
     */
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

    /**
     * 根据状态索引获取枚举
     */
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

    /**
     * 验证状态是否可流转（只能向后流转，不能回退）
     * @param targetStatus 目标状态
     * @return true-可流转，false-不可流转
     */
    public boolean canFlowTo(OrderStatusEnum targetStatus) {
        if (targetStatus == null) {
            return false;
        }
        // 目标状态的索引必须大于当前状态索引
        return targetStatus.getIndex() > this.getIndex();
    }

    /**
     * 静态工具方法：开放给外部，通过状态码判断流转合法性（无需先创建枚举实例）
     * @param currentStatusCode 当前状态码
     * @param targetStatusCode 目标状态码
     * @return true-可流转，false-不可流转
     */
    public static boolean canFlowTo(String currentStatusCode, String targetStatusCode) {
        // 1. 解析状态码为枚举实例
        OrderStatusEnum currentStatus = getByCode(currentStatusCode);
        OrderStatusEnum targetStatus = getByCode(targetStatusCode);

        // 2. 调用成员方法判断
        return currentStatus != null && currentStatus.canFlowTo(targetStatus);
    }

    /**
     * 重载静态方法：通过状态索引判断流转合法性
     * @param currentIndex 当前状态索引（如0）
     * @param targetIndex 目标状态索引（如1）
     * @return true-可流转，false-不可流转
     */
    public static boolean canFlowTo(Integer currentIndex, Integer targetIndex) {
        OrderStatusEnum currentStatus = getByIndex(currentIndex);
        OrderStatusEnum targetStatus = getByIndex(targetIndex);

        return currentStatus != null && currentStatus.canFlowTo(targetStatus);
    }
}
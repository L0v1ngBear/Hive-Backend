package my.hive_back.module.order;

import lombok.Getter;
/**
 * ProcessEnum 属于小程序后端订单模块，属于该领域的细分实现。
 */
@Getter
public enum ProcessEnum {
    /**
     * 原料入库
     */
    MATERIAL_INBOUND(0, "原料入库"),

    /**
     * 原料检验
     */
    MATERIAL_INSPECTION(1, "原料检验"),

    /**
     * 尺寸裁剪
     */
    CUTTING(2, "尺寸裁剪"),

    /**
     * 窗帘缝制
     */
    SEWING(3, "窗帘缝制"),

    /**
     * 窗帘熨烫
     */
    IRONING(4, "窗帘熨烫"),

    /**
     * 成品检验
     */
    FINISHED_INSPECTION(5, "成品检验"),

    /**
     * 高温定型
     */
    HEAT_SETTING(6, "高温定型"),

    /**
     * 打包装箱
     */
    PACKING(7, "打包装箱"),

    /**
     * 成品入库
     */
    FINISHED_INBOUND(8, "成品入库"),

    /**
     * 成品发货
     */
    FINISHED_SHIPPING(9, "成品发货");

    /**
     * 工序编码（对应数据库process字段）
     */
    private final Integer code;

    /**
     * 工序名称（前端展示/日志记录用）
     */
    private final String name;

    // 构造方法
    ProcessEnum(Integer code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 根据工序编码获取枚举
     */
    public static ProcessEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ProcessEnum process : values()) {
            if (process.getCode().equals(code)) {
                return process;
            }
        }
        throw new IllegalArgumentException("无效的生产工序编码：" + code);
    }

    /**
     * 判断当前工序是否是最后一道。
     */
    public boolean isLastProcess() {
        return this == FINISHED_SHIPPING;
    }

    /**
     * 获取下一道工序
     */
    public ProcessEnum getNextProcess() {
        int nextCode = this.getCode() + 1;
        return getByCode(nextCode);
    }
}

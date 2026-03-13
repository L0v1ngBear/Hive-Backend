package my.hive_back.module.order;

import lombok.Getter;

@Getter
public enum ProcessEnum {
    /**
     * 整经（第一道工序）
     */
    WARPING(0, "整经"),

    /**
     * 浆纱（第二道工序）
     */
    SIZING(1, "浆纱"),

    /**
     * 织造（第三道工序）
     */
    WEAVING(2, "织造"),

    /**
     * 验布（第四道工序）
     */
    INSPECTION(3, "验布"),

    /**
     * 卷布（第五道工序）
     */
    WINDING(4, "卷布");

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
     * 判断当前工序是否是最后一道（卷布）
     */
    public boolean isLastProcess() {
        return this == WINDING;
    }

    /**
     * 获取下一道工序
     */
    public ProcessEnum getNextProcess() {
        int nextCode = this.getCode() + 1;
        return getByCode(nextCode);
    }
}

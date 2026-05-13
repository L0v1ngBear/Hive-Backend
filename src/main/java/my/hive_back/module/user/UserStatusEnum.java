package my.hive_back.module.user;

import lombok.Getter;

/**
 * 小程序员工账号状态。
 */
@Getter
public enum UserStatusEnum {

    RESIGNED(0),
    ACTIVE(1),
    PROBATION(2);

    private final Integer code;

    UserStatusEnum(Integer code) {
        this.code = code;
    }

    public boolean matches(Integer value) {
        return code.equals(value);
    }

    public static boolean isUsable(Integer value) {
        return ACTIVE.matches(value) || PROBATION.matches(value);
    }

    public static boolean isResigned(Integer value) {
        return RESIGNED.matches(value);
    }
}

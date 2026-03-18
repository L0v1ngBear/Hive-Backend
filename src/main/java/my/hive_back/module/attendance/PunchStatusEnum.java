package my.hive_back.module.attendance;

import lombok.Getter;

@Getter
public enum PunchStatusEnum {
    NORMAL(0, "正常"),
    LATE(1, "迟到"),
    EARLY(2, "早退"),
    MISS(3, "缺勤"),
    OVERTIME(4, "加班"),
    LEAVE(5, "请假"),
    ABSENT(6, "缺卡");

    private final Integer code;
    private final String info;

    PunchStatusEnum(Integer code, String info) {
        this.code = code;
        this.info = info;
    }
}
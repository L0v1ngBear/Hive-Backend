package my.hive_back.module.attendance;

import lombok.Getter;

@Getter
public enum PunchTypeEnum {
    /**
     * 上班打卡
     */
    WORK_PUNCH(1),
    /**
     * 下班打卡
     */
    OFF_PUNCH(2),
    /**
     * 加班打卡
     */
    OVERTIME_PUNCH(3);

    private int value;

    PunchTypeEnum(int value) {
        this.value = value;
    }
}

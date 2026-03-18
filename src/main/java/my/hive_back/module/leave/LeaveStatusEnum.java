package my.hive_back.module.leave;

import lombok.Getter;

@Getter
public enum LeaveStatusEnum {
    PENDING(1, "待审批"),
    APPROVED(2, "已审批"),
    REJECTED(3, "已拒绝");

    private int code;
    private String desc;

    private LeaveStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}

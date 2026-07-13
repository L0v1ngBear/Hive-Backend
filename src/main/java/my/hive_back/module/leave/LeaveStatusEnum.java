package my.hive_back.module.leave;

import lombok.Getter;
/**
 * LeaveStatusEnum 属于小程序后端请假模块，属于该领域的细分实现。
 */
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

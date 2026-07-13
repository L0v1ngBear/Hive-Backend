package my.hive_back.module.leave;

import lombok.Getter;
/**
 * ApprovalActionEnum 属于小程序后端请假模块，属于该领域的细分实现。
 */
@Getter
public enum ApprovalActionEnum {
    APPROVE(1, "同意"),
    REJECT(2, "拒绝");

    private int code;
    private String desc;

    private ApprovalActionEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}

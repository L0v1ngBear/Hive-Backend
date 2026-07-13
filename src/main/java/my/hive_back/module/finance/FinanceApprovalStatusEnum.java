package my.hive_back.module.finance;

import lombok.Getter;

@Getter
public enum FinanceApprovalStatusEnum {

    PENDING(1);

    private final Integer code;

    FinanceApprovalStatusEnum(Integer code) {
        this.code = code;
    }
}

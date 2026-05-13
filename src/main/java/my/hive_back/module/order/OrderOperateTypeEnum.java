package my.hive_back.module.order;

import lombok.Getter;

@Getter
public enum OrderOperateTypeEnum {

    STATUS_CHANGE("status_change"),
    PROCESS_CHANGE("process_change"),
    UPDATE("update");

    private final String code;

    OrderOperateTypeEnum(String code) {
        this.code = code;
    }
}

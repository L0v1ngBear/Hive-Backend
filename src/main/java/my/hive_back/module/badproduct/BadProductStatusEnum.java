package my.hive_back.module.badproduct;

import lombok.Getter;

@Getter
public enum BadProductStatusEnum {

    PENDING("pending"),
    PROCESSED("processed");

    private final String code;

    BadProductStatusEnum(String code) {
        this.code = code;
    }
}

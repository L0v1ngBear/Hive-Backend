package my.hive_back.module.label;

import lombok.Getter;

@Getter
public enum LabelPrintTypeEnum {

    LABEL("label");

    private final String code;

    LabelPrintTypeEnum(String code) {
        this.code = code;
    }
}

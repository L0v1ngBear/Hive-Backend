package my.hive_back.module.document;

import lombok.Getter;

@Getter
public enum DocumentUploadStatusEnum {

    UPLOADED("UPLOADED");

    private final String code;

    DocumentUploadStatusEnum(String code) {
        this.code = code;
    }
}

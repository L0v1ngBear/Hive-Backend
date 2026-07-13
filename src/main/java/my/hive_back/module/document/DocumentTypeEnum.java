package my.hive_back.module.document;

import lombok.Getter;
/**
 * DocumentTypeEnum 属于小程序后端单据模块，属于该领域的细分实现。
 */
@Getter
public enum DocumentTypeEnum {
    FOLDER(0),
    FILE(1);

    private final Integer type;

    DocumentTypeEnum(Integer type) {
        this.type = type;
    }
   }

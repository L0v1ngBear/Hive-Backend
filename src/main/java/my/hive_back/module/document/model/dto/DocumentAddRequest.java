package my.hive_back.module.document.model.dto;

import lombok.Data;
/**
 * DocumentAddRequest 属于小程序后端单据模块，定义入参结构。
 */
@Data
public class DocumentAddRequest {
    private Long parentId;
    private String name;
}

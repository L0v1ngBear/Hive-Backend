package my.hive_back.module.document.model.dto;

import lombok.Data;

@Data
public class DocumentAddRequest {
    private Long parentId;
    private String name;
}

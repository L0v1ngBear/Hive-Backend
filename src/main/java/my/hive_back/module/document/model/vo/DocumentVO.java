package my.hive_back.module.document.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentVO {
    private Long id;
    private Long parentId;
    private String name;
    private String originalName;
    private Integer type;
    private String fileUrl;
    private String storageProvider;
    private String storageBucket;
    private String storageObjectKey;
    private Long fileSize;
    private String fileExt;
    private String mimeType;
    private String uploadStatus;
    private LocalDateTime createTime;
}

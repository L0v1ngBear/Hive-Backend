package my.hive_back.module.document.model.vo;

import java.time.LocalDateTime;

public class DocumentVO {
    private Long id;
    private Long parentId;
    private String name;
    private Integer type; // 1-文件夹, 2-文件
    private String fileUrl;
    private Long fileSize; // 前端可格式化为 MB/KB
    private String fileExt;
    private LocalDateTime createTime;
}

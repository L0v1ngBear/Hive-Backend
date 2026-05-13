package my.hive_back.common.storage;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FileUploadResult {

    private String originalName;

    private String storageProvider;

    private String bucketName;

    private String objectKey;

    private String url;

    private Long fileSize;

    private String fileExt;

    private String mimeType;

    private String fileHash;

    private String etag;
}

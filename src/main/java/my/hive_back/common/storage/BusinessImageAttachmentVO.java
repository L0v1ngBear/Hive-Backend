package my.hive_back.common.storage;

import lombok.Data;

/**
 * Mini-program image upload response stored under /uploads.
 */
@Data
public class BusinessImageAttachmentVO {

    private String fileName;

    private String fileUrl;

    private Long fileSize;
}

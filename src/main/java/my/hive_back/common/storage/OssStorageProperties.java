package my.hive_back.common.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "storage.oss")
public class OssStorageProperties {

    private boolean enabled = false;

    private String endpoint;

    private String bucketName;

    private String accessKeyId;

    private String accessKeySecret;

    private String publicBaseUrl;

    private String pathPrefix = "hive";

    private long maxFileSizeMb = 20;

    private List<String> allowedExtensions = new ArrayList<>(Arrays.asList(
            "jpg", "jpeg", "png", "pdf", "doc", "docx", "xls", "xlsx"
    ));

    private List<String> allowedContentTypes = new ArrayList<>(Arrays.asList(
            "image/jpeg",
            "image/png",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    ));
}

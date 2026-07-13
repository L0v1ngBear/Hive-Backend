package my.hive_back.common.storage;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectResult;
import lombok.extern.slf4j.Slf4j;
import my.hive.common.external.ExternalApiGuardService;
import my.hive.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OssStorageService {

    private static final String STORAGE_PROVIDER = "ALIYUN_OSS";

    private final OssStorageProperties properties;
    private final ExternalApiGuardService externalApiGuardService;

    @Value("${external-api.guard.oss.max-uploads-per-tenant-window:120}")
    private Integer ossMaxUploadsPerTenantWindow;

    @Value("${external-api.guard.oss.upload-window-seconds:3600}")
    private Integer ossUploadWindowSeconds;

    public OssStorageService(OssStorageProperties properties, ExternalApiGuardService externalApiGuardService) {
        this.properties = properties;
        this.externalApiGuardService = externalApiGuardService;
    }

    public FileUploadResult upload(MultipartFile file, String tenantCode) {
        validateConfig();
        FileCandidate candidate = validateFile(file, tenantCode);
        externalApiGuardService.checkRateLimit(
                "aliyun-oss",
                "upload",
                candidate.tenantCode(),
                ossMaxUploadsPerTenantWindow == null ? 120 : ossMaxUploadsPerTenantWindow,
                Duration.ofSeconds(ossUploadWindowSeconds == null ? 3600 : Math.max(1, ossUploadWindowSeconds))
        );
        String objectKey = buildObjectKey(candidate.tenantCode(), candidate.fileExt());
        String fileHash = sha256(file);

        OSS ossClient = new OSSClientBuilder().build(
                properties.getEndpoint(),
                properties.getAccessKeyId(),
                properties.getAccessKeySecret()
        );
        try (InputStream inputStream = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(candidate.fileSize());
            metadata.setContentType(candidate.mimeType());
            metadata.addUserMetadata("tenant-code", candidate.tenantCode());
            metadata.addUserMetadata("original-name", safeMetadataValue(candidate.originalName()));
            metadata.addUserMetadata("sha256", fileHash);

            PutObjectResult result = ossClient.putObject(properties.getBucketName(), objectKey, inputStream, metadata);
            return FileUploadResult.builder()
                    .originalName(candidate.originalName())
                    .storageProvider(STORAGE_PROVIDER)
                    .bucketName(properties.getBucketName())
                    .objectKey(objectKey)
                    .url(buildPublicUrl(objectKey))
                    .fileSize(candidate.fileSize())
                    .fileExt(candidate.fileExt())
                    .mimeType(candidate.mimeType())
                    .fileHash(fileHash)
                    .etag(result == null ? null : result.getETag())
                    .build();
        } catch (OSSException | ClientException e) {
            log.error("aliyun oss upload failed, tenantCode={}, objectKey={}, errorCode={}",
                    candidate.tenantCode(), objectKey, e instanceof OSSException ossEx ? ossEx.getErrorCode() : e.getMessage(), e);
            throw new BusinessException("文件上传失败，请稍后重试");
        } catch (IOException e) {
            log.error("read upload file failed, tenantCode={}, originalName={}", candidate.tenantCode(), candidate.originalName(), e);
            throw new BusinessException("读取上传文件失败，请重新选择文件");
        } finally {
            ossClient.shutdown();
        }
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public void deleteQuietly(String objectKey) {
        if (!properties.isEnabled() || !StringUtils.hasText(objectKey)) {
            return;
        }
        OSS ossClient = null;
        try {
            ossClient = new OSSClientBuilder().build(
                    properties.getEndpoint(),
                    properties.getAccessKeyId(),
                    properties.getAccessKeySecret()
            );
            ossClient.deleteObject(properties.getBucketName(), objectKey);
        } catch (Exception e) {
            log.warn("delete aliyun oss object failed, objectKey={}", objectKey, e);
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }

    private void validateConfig() {
        if (!properties.isEnabled()) {
            throw new BusinessException("文件上传暂未开启，请联系管理员配置 OSS");
        }
        if (!StringUtils.hasText(properties.getEndpoint())
                || !StringUtils.hasText(properties.getBucketName())
                || !StringUtils.hasText(properties.getAccessKeyId())
                || !StringUtils.hasText(properties.getAccessKeySecret())) {
            throw new BusinessException("OSS 配置不完整，请联系管理员");
        }
        if (properties.getMaxFileSizeMb() <= 0 || properties.getMaxFileSizeMb() > 1024) {
            throw new BusinessException("OSS 文件大小限制配置不合法");
        }
    }

    private FileCandidate validateFile(MultipartFile file, String tenantCode) {
        if (!StringUtils.hasText(tenantCode)) {
            throw new BusinessException("租户信息缺失，无法上传文件");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择需要上传的文件");
        }
        long size = file.getSize();
        if (size <= 0) {
            throw new BusinessException("文件内容为空，无法上传");
        }
        long maxBytes = properties.getMaxFileSizeMb() * 1024L * 1024L;
        if (size > maxBytes) {
            throw new BusinessException("文件大小不能超过 " + properties.getMaxFileSizeMb() + "MB");
        }

        String originalName = normalizeOriginalName(file.getOriginalFilename());
        String fileExt = extractExtension(originalName);
        Set<String> allowedExtensions = normalizeSet(properties.getAllowedExtensions());
        if (!allowedExtensions.isEmpty() && !allowedExtensions.contains(fileExt)) {
            throw new BusinessException("不支持的文件类型：" + fileExt);
        }

        String mimeType = StringUtils.hasText(file.getContentType())
                ? file.getContentType().toLowerCase(Locale.ROOT)
                : "application/octet-stream";
        Set<String> allowedContentTypes = normalizeSet(properties.getAllowedContentTypes());
        if (!"application/octet-stream".equals(mimeType)
                && !allowedContentTypes.isEmpty()
                && !allowedContentTypes.contains(mimeType)) {
            throw new BusinessException("不支持的文件内容类型：" + mimeType);
        }

        return new FileCandidate(sanitizePathSegment(tenantCode), originalName, fileExt, mimeType, size);
    }

    private String normalizeOriginalName(String originalFilename) {
        String originalName = StringUtils.hasText(originalFilename) ? StringUtils.cleanPath(originalFilename.trim()) : "upload-file";
        if (originalName.contains("..") || originalName.contains("/") || originalName.contains("\\")) {
            throw new BusinessException("文件名不合法");
        }
        if (originalName.length() > 180) {
            int dotIndex = originalName.lastIndexOf('.');
            String ext = dotIndex >= 0 ? originalName.substring(dotIndex) : "";
            int maxBaseLength = Math.max(1, 180 - ext.length());
            originalName = originalName.substring(0, Math.min(maxBaseLength, originalName.length())) + ext;
        }
        return originalName;
    }

    private String extractExtension(String originalName) {
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalName.length() - 1) {
            throw new BusinessException("文件缺少扩展名");
        }
        return originalName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String buildObjectKey(String tenantCode, String fileExt) {
        LocalDate today = LocalDate.now();
        String prefix = sanitizePrefix(properties.getPathPrefix());
        return prefix + "/" + tenantCode + "/" + today.getYear() + "/"
                + String.format("%02d", today.getMonthValue()) + "/"
                + String.format("%02d", today.getDayOfMonth()) + "/"
                + UUID.randomUUID() + "." + fileExt;
    }

    private String buildPublicUrl(String objectKey) {
        if (StringUtils.hasText(properties.getPublicBaseUrl())) {
            return trimTrailingSlash(properties.getPublicBaseUrl()) + "/" + encodeObjectKey(objectKey);
        }
        String endpoint = properties.getEndpoint().replaceFirst("^https?://", "");
        return "https://" + properties.getBucketName() + "." + endpoint + "/" + encodeObjectKey(objectKey);
    }

    private String sha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = file.getInputStream();
                 DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
                byte[] buffer = new byte[8192];
                while (digestInputStream.read(buffer) != -1) {
                    // DigestInputStream updates the digest as bytes are read.
                }
            }
            StringBuilder builder = new StringBuilder();
            for (byte b : digest.digest()) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            log.error("calculate upload file hash failed", e);
            throw new BusinessException("文件校验失败，请重新上传");
        }
    }

    private Set<String> normalizeSet(java.util.List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return Set.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private String sanitizePrefix(String prefix) {
        String normalized = StringUtils.hasText(prefix) ? prefix.trim() : "hive";
        normalized = normalized.replace('\\', '/').replaceAll("/{2,}", "/");
        normalized = normalized.replaceAll("^/+", "").replaceAll("/+$", "");
        normalized = normalized.replace("..", "");
        normalized = normalized.replaceAll("[^A-Za-z0-9_./-]", "-");
        return StringUtils.hasText(normalized) ? normalized : "hive";
    }

    private String sanitizePathSegment(String value) {
        String normalized = value.replaceAll("[^A-Za-z0-9_-]", "-");
        return StringUtils.hasText(normalized) ? normalized : "tenant";
    }

    private String safeMetadataValue(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]", " ");
    }

    private String trimTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }

    private String encodeObjectKey(String objectKey) {
        return java.util.Arrays.stream(objectKey.split("/"))
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
    }

    private record FileCandidate(String tenantCode, String originalName, String fileExt, String mimeType, long fileSize) {
    }
}

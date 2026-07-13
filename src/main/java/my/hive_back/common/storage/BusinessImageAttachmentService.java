package my.hive_back.common.storage;

import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive_back.common.security.InternalUploadUrlValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class BusinessImageAttachmentService {

    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024L;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Set<String> ALLOWED_MODULES = Set.of("bad-product", "finance", "inventory-recognition");
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp");

    @Value("${app.upload.root:uploads}")
    private String uploadRoot;

    public BusinessImageAttachmentVO uploadImage(MultipartFile file, String module) {
        String normalizedModule = normalizeModule(module);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择需要上传的图片");
        }
        if (file.getSize() <= 0) {
            throw new BusinessException("图片内容为空，无法上传");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessException("图片大小不能超过 5MB");
        }

        String originalFilename = normalizeFilename(file.getOriginalFilename());
        String normalizedExtension = resolveImageExtension(originalFilename, file.getContentType());
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(normalizedExtension)) {
            throw new BusinessException("小程序仅支持上传 PNG、JPG、JPEG、WEBP 图片");
        }

        String tenantFolder = safeTenantFolder();
        String dateFolder = LocalDate.now().format(DATE_FORMATTER);
        Path rootPath = Paths.get(uploadRoot).toAbsolutePath().normalize();
        Path targetDir = rootPath.resolve(normalizedModule).resolve(tenantFolder).resolve(dateFolder).normalize();
        if (!targetDir.startsWith(rootPath)) {
            throw new BusinessException("图片存储路径不合法");
        }

        String storedFilename = UUID.randomUUID().toString().replace("-", "") + "." + normalizedExtension;
        Path targetPath = targetDir.resolve(storedFilename).normalize();
        if (!targetPath.startsWith(rootPath)) {
            throw new BusinessException("图片存储路径不合法");
        }

        try {
            Files.createDirectories(targetDir);
            file.transferTo(targetPath);
        } catch (IOException e) {
            throw new BusinessException("图片上传失败，请稍后重试");
        }

        BusinessImageAttachmentVO vo = new BusinessImageAttachmentVO();
        vo.setFileName(ensureDisplayFilename(originalFilename, normalizedExtension));
        vo.setFileSize(file.getSize());
        vo.setFileUrl("/uploads/" + normalizedModule + "/" + tenantFolder + "/" + dateFolder + "/" + storedFilename);
        return vo;
    }

    public org.springframework.core.io.Resource load(String attachmentUrl, String module) {
        String normalizedModule = normalizeModule(module);
        String relativePath = InternalUploadUrlValidator.normalizeRelativeUploadPath(
                attachmentUrl,
                TenantPermissionContext.getTenantCode(),
                normalizedModule
        );
        if (!StringUtils.hasText(relativePath)) {
            throw new BusinessException("图片地址不能为空");
        }

        Path rootPath = Paths.get(uploadRoot).toAbsolutePath().normalize();
        Path moduleRoot = rootPath.resolve(normalizedModule).normalize();
        Path targetPath = rootPath.resolve(relativePath).normalize();
        if (!targetPath.startsWith(moduleRoot) || !Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
            throw new BusinessException("图片不存在或已被移除");
        }
        return new FileSystemResource(targetPath);
    }

    private String normalizeModule(String module) {
        if (!StringUtils.hasText(module)) {
            throw new BusinessException("图片业务模块不能为空");
        }
        String normalized = module.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_MODULES.contains(normalized)) {
            throw new BusinessException("不支持的图片业务模块");
        }
        return normalized;
    }

    private String normalizeFilename(String originalFilename) {
        String originalName = StringUtils.hasText(originalFilename)
                ? StringUtils.cleanPath(originalFilename.trim())
                : "image.jpg";
        if (originalName.contains("..") || originalName.contains("/") || originalName.contains("\\")) {
            throw new BusinessException("图片文件名不合法");
        }
        if (originalName.length() > 180) {
            String extension = StringUtils.getFilenameExtension(originalName);
            String suffix = StringUtils.hasText(extension) ? "." + extension : "";
            int maxBaseLength = Math.max(1, 180 - suffix.length());
            originalName = originalName.substring(0, Math.min(maxBaseLength, originalName.length())) + suffix;
        }
        return originalName;
    }

    private String resolveImageExtension(String originalFilename, String contentType) {
        String extension = StringUtils.getFilenameExtension(originalFilename);
        String normalizedExtension = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        if (ALLOWED_IMAGE_EXTENSIONS.contains(normalizedExtension)) {
            return normalizedExtension;
        }
        if (!StringUtils.hasText(contentType)) {
            return normalizedExtension;
        }
        String normalizedContentType = contentType.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedContentType) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> normalizedExtension;
        };
    }

    private String ensureDisplayFilename(String originalFilename, String extension) {
        if (StringUtils.hasText(StringUtils.getFilenameExtension(originalFilename))) {
            return originalFilename;
        }
        return originalFilename + "." + extension;
    }

    private String safeTenantFolder() {
        String tenantCode = TenantPermissionContext.getTenantCode();
        if (!StringUtils.hasText(tenantCode)) {
            throw new BusinessException("组织信息缺失，无法上传图片");
        }
        String normalized = tenantCode.trim().replaceAll("[^A-Za-z0-9_-]", "_");
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException("组织信息不合法，无法上传图片");
        }
        return normalized;
    }
}

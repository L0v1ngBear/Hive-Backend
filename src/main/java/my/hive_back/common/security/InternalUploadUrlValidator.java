package my.hive_back.common.security;

import my.hive.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Validates upload URLs that are stored in business tables.
 */
public final class InternalUploadUrlValidator {

    private static final int MAX_URL_LENGTH = 512;

    private InternalUploadUrlValidator() {
    }

    public static String normalizeOptionalFinanceAttachment(String value, String tenantCode) {
        String path = normalizeRelativeUploadPath(value, tenantCode, "finance", "sales-order");
        if (path == null) {
            return null;
        }
        return "/uploads/" + path;
    }

    public static String normalizeStoredUploadUrl(String value, String tenantCode, String module) {
        String path = normalizeRelativeUploadPath(value, tenantCode, module);
        if (path == null) {
            return null;
        }
        return "/uploads/" + path;
    }

    public static String normalizeRelativeUploadPath(String value, String tenantCode, String... modules) {
        String path = normalize(value);
        if (path == null) {
            return null;
        }
        String tenantSegment = safeTenantSegment(tenantCode);
        for (String module : modules) {
            if (StringUtils.hasText(module) && path.startsWith(module.trim() + "/" + tenantSegment + "/")) {
                return path;
            }
        }
        throw new BusinessException("附件不存在或无权访问");
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String path = value.trim().replace('\\', '/');
        if (path.length() > MAX_URL_LENGTH
                || containsControl(path)
                || path.startsWith("//")
                || hasScheme(path)
                || path.contains("?")
                || path.contains("#")
                || path.contains("..")
                || path.toLowerCase(Locale.ROOT).contains("%2e")) {
            throw new BusinessException("附件地址不合法");
        }

        if (path.startsWith("/uploads/")) {
            path = path.substring("/uploads/".length());
        } else if (path.startsWith("uploads/")) {
            path = path.substring("uploads/".length());
        } else {
            throw new BusinessException("附件地址必须来自系统上传目录");
        }
        if (!StringUtils.hasText(path) || path.startsWith("/") || path.endsWith("/")) {
            throw new BusinessException("附件地址不合法");
        }
        return path;
    }

    private static boolean containsControl(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasScheme(String value) {
        return value.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*");
    }

    private static String safeTenantSegment(String tenantCode) {
        if (!StringUtils.hasText(tenantCode)) {
            throw new BusinessException("租户信息缺失");
        }
        String normalized = tenantCode.trim().replaceAll("[^A-Za-z0-9_-]", "_");
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException("租户信息不合法");
        }
        return normalized;
    }
}

package my.hive_back.common.tenant;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Keeps tenant isolation, but limits the product to a small fixed tenant set.
 */
@Component
@ConfigurationProperties(prefix = "hive.tenant")
public class BoundedTenantProperties {

    private String mode = "BOUNDED";
    private String defaultCode = "TENANT_001";
    private String allowedCodes = "TENANT_001,TENANT_002";
    private int maxCount = 2;

    public List<String> allowedTenantCodes() {
        Set<String> codes = new LinkedHashSet<>();
        if (allowedCodes != null && !allowedCodes.isBlank()) {
            Arrays.stream(allowedCodes.split(","))
                    .map(this::normalizeTenantCode)
                    .filter(code -> code != null && !code.isBlank())
                    .forEach(codes::add);
        }
        String defaultTenantCode = defaultTenantCode();
        if (codes.isEmpty()) {
            codes.add(defaultTenantCode);
        }
        if (!codes.contains(defaultTenantCode)) {
            codes.add(defaultTenantCode);
        }
        if (maxCount > 0 && codes.size() > maxCount) {
            throw new IllegalStateException("hive.tenant.allowed-codes cannot contain more than " + maxCount + " tenants");
        }
        return List.copyOf(codes);
    }

    public String defaultTenantCode() {
        String normalized = normalizeTenantCode(defaultCode);
        return normalized == null || normalized.isBlank() ? "TENANT_001" : normalized;
    }

    public boolean isTenantAllowed(String tenantCode) {
        String normalized = normalizeTenantCode(tenantCode);
        return normalized != null && allowedTenantCodes().contains(normalized);
    }

    public boolean isBoundedMode() {
        return mode == null || !"OPEN".equals(mode.trim().toUpperCase(Locale.ROOT));
    }

    public void assertTenantAllowed(String tenantCode) {
        if (isBoundedMode() && !isTenantAllowed(tenantCode)) {
            throw new IllegalArgumentException("tenant is not in allowed tenant list");
        }
    }

    private String normalizeTenantCode(String tenantCode) {
        if (tenantCode == null) {
            return null;
        }
        String normalized = tenantCode.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getDefaultCode() {
        return defaultCode;
    }

    public void setDefaultCode(String defaultCode) {
        this.defaultCode = defaultCode;
    }

    public String getAllowedCodes() {
        return allowedCodes;
    }

    public void setAllowedCodes(String allowedCodes) {
        this.allowedCodes = allowedCodes;
    }

    public int getMaxCount() {
        return maxCount;
    }

    public void setMaxCount(int maxCount) {
        this.maxCount = maxCount;
    }
}

package my.hive_back.common.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import my.hive.common.exception.BusinessException;
import my.hive_back.common.enums.CommonStatusEnum;
import my.hive_back.common.enums.DeleteFlagEnum;
import my.hive_back.common.enums.PlatformTenantEnum;
import my.hive_back.module.tenant.mapper.TenantMapper;
import my.hive_back.module.tenant.TenantFeatureEnum;
import my.hive_back.module.tenant.TenantPlanEnum;
import my.hive_back.module.tenant.TenantSubscriptionStatusEnum;
import my.hive_back.module.tenant.model.entity.Tenant;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@Slf4j
public class TenantFeatureService {

    private static final Set<String> BASE_MODULE_FEATURES = Set.of(
            TenantFeatureEnum.CODE_MODULE_DASHBOARD,
            TenantFeatureEnum.CODE_MODULE_ORDER,
            TenantFeatureEnum.CODE_MODULE_INVENTORY,
            TenantFeatureEnum.CODE_MODULE_BAD_PRODUCT,
            TenantFeatureEnum.CODE_MODULE_CUSTOMER,
            TenantFeatureEnum.CODE_MODULE_PRICE,
            TenantFeatureEnum.CODE_MODULE_RECEIPT,
            TenantFeatureEnum.CODE_MODULE_APPROVAL,
            TenantFeatureEnum.CODE_MODULE_ATTENDANCE,
            TenantFeatureEnum.CODE_MODULE_EMPLOYEE,
            TenantFeatureEnum.CODE_MODULE_ROLE,
            TenantFeatureEnum.CODE_MODULE_LABEL,
            TenantFeatureEnum.CODE_MODULE_DOCUMENT,
            TenantFeatureEnum.CODE_MODULE_MANUAL
    );
    private static final Pattern FEATURE_KEY_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_.:\\-]{0,100}$");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Resource
    private TenantMapper tenantMapper;

    public void requireFeatureEnabled(String tenantCode, String featureName, String message) {
        if (isSuperTenant(tenantCode)) {
            return;
        }
        Tenant tenant = requireTenant(tenantCode);
        if (!isTenantUsable(tenant) || !isFeatureEnabled(tenant, featureName)) {
            throw new BusinessException(403, StringUtils.hasText(message) ? message : "当前套餐暂未开放该功能，请联系平台管理员开通");
        }
    }

    private boolean isFeatureEnabled(Tenant tenant, String featureName) {
        if (tenant == null || !StringUtils.hasText(featureName)) {
            return false;
        }
        String normalized = normalizeFeatureKey(featureName, false);
        return StringUtils.hasText(normalized) && buildFeatureKeys(tenant).contains(normalized);
    }

    private Tenant requireTenant(String tenantCode) {
        if (!StringUtils.hasText(tenantCode)) {
            throw new BusinessException(401, "登录状态异常，请重新登录");
        }
        Tenant tenant = tenantMapper.selectByTenantCode(tenantCode.trim());
        if (tenant == null) {
            throw new BusinessException(403, "租户不存在或已被停用");
        }
        return tenant;
    }

    private boolean isTenantUsable(Tenant tenant) {
        if (tenant == null || DeleteFlagEnum.DELETED.matches(tenant.getDeleted()) || !CommonStatusEnum.ENABLED.matches(tenant.getStatus())) {
            return false;
        }
        String status = StringUtils.hasText(tenant.getSubscriptionStatus()) ? tenant.getSubscriptionStatus().trim().toUpperCase() : "";
        if (TenantSubscriptionStatusEnum.SUSPENDED.matches(status) || TenantSubscriptionStatusEnum.EXPIRED.matches(status)) {
            return false;
        }
        LocalDateTime endTime = tenant.getSubscriptionEndTime();
        return endTime == null || !endTime.isBefore(LocalDateTime.now());
    }

    private boolean isSuperTenant(String tenantCode) {
        return StringUtils.hasText(tenantCode) && PlatformTenantEnum.SUPER.matches(tenantCode);
    }

    private Set<String> buildFeatureKeys(Tenant tenant) {
        LinkedHashSet<String> enabled = new LinkedHashSet<>(baseFeatureKeys(tenant.getPackageCode()));
        String flags = StringUtils.hasText(tenant.getFeatureFlags())
                ? tenant.getFeatureFlags()
                : defaultFeatureFlags(tenant.getPackageCode());
        try {
            JsonNode root = OBJECT_MAPPER.readTree(flags);
            if (root.isObject()) {
                applyFeatureNode(root, "", enabled);
            }
        } catch (Exception ex) {
            log.warn("invalid tenant feature flags, tenantCode={}", tenant.getTenantCode(), ex);
        }
        return Collections.unmodifiableSet(enabled);
    }

    private Set<String> baseFeatureKeys(String planCode) {
        LinkedHashSet<String> features = new LinkedHashSet<>(BASE_MODULE_FEATURES);
        features.add(TenantFeatureEnum.CODE_AI_ADVICE);
        if (TenantPlanEnum.PROFESSIONAL.matches(planCode) || TenantPlanEnum.PRIVATE.matches(planCode)) {
            features.add(TenantFeatureEnum.CODE_ADVANCED_AI);
        }
        return features;
    }

    private String defaultFeatureFlags(String planCode) {
        boolean advancedAi = TenantPlanEnum.PROFESSIONAL.matches(planCode) || TenantPlanEnum.PRIVATE.matches(planCode);
        return "{\"aiAdvice\":true,\"advancedAi\":" + advancedAi + ",\"custom\":{},\"modules\":{}}";
    }

    private void applyFeatureNode(JsonNode node, String path, Set<String> enabled) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isBoolean()) {
            applyFeatureValue(path, node.asBoolean(), enabled);
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item != null && item.isTextual()) {
                    applyFeatureValue(item.asText(), true, enabled);
                }
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String childPath = path.isBlank() ? field.getKey() : path + "." + field.getKey();
            applyFeatureNode(field.getValue(), childPath, enabled);
        }
    }

    private void applyFeatureValue(String rawFeatureKey, boolean enabledFlag, Set<String> enabled) {
        String featureKey = normalizeFeatureKey(rawFeatureKey, false);
        if (!StringUtils.hasText(featureKey)) {
            return;
        }
        if (enabledFlag) {
            enabled.add(featureKey);
        } else {
            enabled.remove(featureKey);
        }
    }

    private String normalizeFeatureKey(String rawFeatureKey, boolean strict) {
        if (!StringUtils.hasText(rawFeatureKey)) {
            return null;
        }
        String featureKey = rawFeatureKey.trim();
        if (featureKey.startsWith("modules.")) {
            featureKey = "module." + featureKey.substring("modules.".length());
        }
        if ("module".equals(featureKey)
                || "custom".equals(featureKey)
                || featureKey.startsWith("enabledFeatures.")
                || featureKey.startsWith("features.")
                || featureKey.startsWith("platform.")) {
            return null;
        }
        if (!FEATURE_KEY_PATTERN.matcher(featureKey).matches()) {
            if (strict) {
                throw new BusinessException("非法功能码：" + rawFeatureKey);
            }
            return null;
        }
        return featureKey;
    }
}

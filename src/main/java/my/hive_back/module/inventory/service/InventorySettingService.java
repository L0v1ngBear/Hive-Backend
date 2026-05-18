package my.hive_back.module.inventory.service;

import jakarta.annotation.Resource;
import my.hive.common.redis.HiveRedisKeyBuilder;
import my.hive_back.module.inventory.mapper.InventorySettingMapper;
import my.hive_back.module.inventory.model.entity.InventorySetting;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

@Service
public class InventorySettingService {

    private static final BigDecimal DEFAULT_WARNING_THRESHOLD = new BigDecimal("100.00");
    private static final Duration WARNING_THRESHOLD_CACHE_TTL = Duration.ofHours(6);

    @Resource
    private InventorySettingMapper inventorySettingMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private HiveRedisKeyBuilder redisKeyBuilder;

    public BigDecimal warningThreshold(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            return DEFAULT_WARNING_THRESHOLD;
        }
        String cacheKey = warningThresholdCacheKey(tenantCode);
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isBlank()) {
                return normalizeThreshold(new BigDecimal(cached));
            }
        } catch (Exception ignored) {
            try {
                stringRedisTemplate.delete(cacheKey);
            } catch (Exception deleteIgnored) {
            }
        }

        InventorySetting setting = inventorySettingMapper.selectByTenantCode(tenantCode);
        BigDecimal threshold = setting == null || setting.getWarningThresholdMeters() == null
                ? DEFAULT_WARNING_THRESHOLD
                : normalizeThreshold(setting.getWarningThresholdMeters());
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, threshold.toPlainString(), WARNING_THRESHOLD_CACHE_TTL);
        } catch (Exception ignored) {
        }
        return threshold;
    }

    private BigDecimal normalizeThreshold(BigDecimal value) {
        if (value == null) {
            return DEFAULT_WARNING_THRESHOLD;
        }
        BigDecimal threshold = value.setScale(2, RoundingMode.HALF_UP);
        return threshold.compareTo(BigDecimal.ZERO) < 0 ? DEFAULT_WARNING_THRESHOLD : threshold;
    }

    private String warningThresholdCacheKey(String tenantCode) {
        return redisKeyBuilder.cache("inventory", "warning-threshold", tenantCode);
    }
}

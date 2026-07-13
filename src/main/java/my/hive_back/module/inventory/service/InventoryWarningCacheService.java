package my.hive_back.module.inventory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import my.hive.common.redis.HiveRedisKeyBuilder;
import my.hive.common.utils.RedisCacheHelper;
import my.hive_back.module.inventory.mapper.ClothMapper;
import my.hive_back.module.inventory.model.vo.InventoryRecordVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class InventoryWarningCacheService {

    private static final Duration WARNING_CACHE_TTL = Duration.ofMinutes(2);
    private static final int SNAPSHOT_LIMIT = 20;

    @Resource
    private ClothMapper clothMapper;

    @Resource
    private InventorySettingService inventorySettingService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private HiveRedisKeyBuilder redisKeyBuilder;

    @Resource
    private RedisCacheHelper redisCacheHelper;

    public List<InventoryRecordVO> warningList(String tenantCode, int limit) {
        if (tenantCode == null || tenantCode.isBlank() || limit <= 0) {
            return List.of();
        }
        return loadSnapshot(tenantCode).warnings.stream()
                .limit(Math.min(limit, SNAPSHOT_LIMIT))
                .map(this::toRecordVO)
                .toList();
    }

    public void invalidate(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            return;
        }
        redisCacheHelper.deleteByPattern(redisKeyBuilder.cachePattern("inventory", "warning", tenantCode, "*"));
    }

    private WarningSnapshot loadSnapshot(String tenantCode) {
        BigDecimal threshold = inventorySettingService.warningThreshold(tenantCode);
        String cacheKey = redisKeyBuilder.cache("inventory", "warning", tenantCode, threshold.stripTrailingZeros().toPlainString());
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isBlank()) {
                WarningSnapshot snapshot = objectMapper.readValue(cached, WarningSnapshot.class);
                return snapshot == null ? WarningSnapshot.empty() : snapshot.normalized();
            }
        } catch (Exception exception) {
            log.warn("Read inventory warning cache failed, tenantCode={}", tenantCode, exception);
        }

        WarningSnapshot snapshot = new WarningSnapshot();
        Long count = clothMapper.countWarningModels(tenantCode, threshold);
        snapshot.count = count == null ? 0L : count;
        List<InventoryRecordVO> warnings = clothMapper.selectWarningModels(tenantCode, threshold, SNAPSHOT_LIMIT);
        snapshot.warnings = warnings == null ? List.of() : warnings.stream().map(this::fromRecordVO).toList();
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(snapshot), WARNING_CACHE_TTL);
        } catch (Exception exception) {
            log.warn("Write inventory warning cache failed, tenantCode={}", tenantCode, exception);
        }
        return snapshot;
    }

    private WarningItem fromRecordVO(InventoryRecordVO row) {
        WarningItem item = new WarningItem();
        if (row == null) {
            return item;
        }
        item.id = row.getId();
        item.modelCode = row.getModelCode();
        item.totalMeters = row.getMeters() == null ? BigDecimal.ZERO : BigDecimal.valueOf(row.getMeters());
        item.latestTime = row.getCreateTime();
        return item;
    }

    private InventoryRecordVO toRecordVO(WarningItem item) {
        InventoryRecordVO vo = new InventoryRecordVO();
        if (item == null) {
            return vo;
        }
        vo.setId(item.id);
        vo.setModelCode(item.modelCode);
        vo.setMeters(item.totalMeters == null ? 0F : item.totalMeters.floatValue());
        vo.setCreateTime(item.latestTime);
        return vo;
    }

    public static class WarningSnapshot {
        public long count;
        public List<WarningItem> warnings = List.of();

        static WarningSnapshot empty() {
            return new WarningSnapshot();
        }

        WarningSnapshot normalized() {
            if (warnings == null) {
                warnings = List.of();
            }
            return this;
        }
    }

    public static class WarningItem {
        public Long id;
        public String modelCode;
        public BigDecimal totalMeters;
        public LocalDateTime latestTime;
    }
}

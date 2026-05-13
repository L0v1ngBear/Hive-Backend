package my.hive_back.module.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive.common.redis.HiveRedisKeyBuilder;
import my.hive_back.common.enums.BinaryFlagEnum;
import my.hive_back.common.enums.CommonStatusEnum;
import my.hive_back.module.tenant.mapper.TenantAttendanceRuleMapper;
import my.hive_back.module.tenant.mapper.TenantMapper;
import my.hive_back.module.tenant.model.dto.TenantLocationAddRequest;
import my.hive_back.module.tenant.model.entity.TenantAttendanceRule;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalTime;
/**
 * TenantService 属于小程序后端租户模块，实现核心业务编排与规则逻辑。
 */
@Service
public class TenantService {

    private static final String LEGACY_COMPANY_ATTENDANCE_RULE_KEY = "companyAttendanceRule";
    private static final Duration ATTENDANCE_RULE_CACHE_TTL = Duration.ofHours(6);
    private static final double DEFAULT_RADIUS = 300D;
    private static final double MAX_RADIUS = 10000D;

    @Resource
    private TenantMapper tenantMapper;

    @Resource
    private TenantAttendanceRuleMapper tenantLocationMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private HiveRedisKeyBuilder redisKeyBuilder;

    public TenantAttendanceRule saveTenantLocation(TenantLocationAddRequest request) {
        String tenantCode = TenantPermissionContext.getTenantCode();
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new BusinessException("没有权限或租户不存在");
        }
        validateLocation(request);

        TenantAttendanceRule rule = tenantLocationMapper.selectOne(new LambdaQueryWrapper<TenantAttendanceRule>()
                .eq(TenantAttendanceRule::getTenantCode, tenantCode)
                .last("LIMIT 1"));
        if (rule == null) {
            rule = buildDefaultRule(tenantCode);
        }

        rule.setLatitude(request.getLatitude());
        rule.setLongitude(request.getLongitude());
        rule.setAddress(clean(request.getAddress(), "小程序定位设置"));
        rule.setRadius(safeRadius(request.getRadius()));
        rule.setEnableGps(BinaryFlagEnum.YES.getCode());
        rule.setStatus(CommonStatusEnum.ENABLED.getCode());

        if (rule.getId() == null) {
            tenantLocationMapper.insert(rule);
        } else {
            tenantLocationMapper.updateById(rule);
        }
        evictAttendanceRuleCache(tenantCode);
        return rule;
    }

    public TenantAttendanceRule getTenantLocation() {
        String tenantCode = TenantPermissionContext.getTenantCode();
        if (tenantCode == null) {
            throw new BusinessException("没有权限或租户不存在");
        }
        TenantAttendanceRule tenantLocation = getCachedAttendanceRule(tenantCode);
        if (tenantLocation == null) {
            LambdaQueryWrapper<TenantAttendanceRule> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(TenantAttendanceRule::getTenantCode, tenantCode);
            tenantLocation = tenantLocationMapper.selectOne(queryWrapper);
            cacheAttendanceRule(tenantCode, tenantLocation);
        }
        if (tenantLocation == null) {
            throw new BusinessException("租户不存在");
        }
        return tenantLocation;
    }

    private TenantAttendanceRule getCachedAttendanceRule(String tenantCode) {
        try {
            String cached = stringRedisTemplate.opsForValue().get(redisKeyBuilder.cache("tenant", "attendance-rule", tenantCode));
            if (cached == null || cached.isBlank()) {
                return null;
            }
            return objectMapper.readValue(cached, TenantAttendanceRule.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void cacheAttendanceRule(String tenantCode, TenantAttendanceRule rule) {
        if (rule == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(
                    redisKeyBuilder.cache("tenant", "attendance-rule", tenantCode),
                    objectMapper.writeValueAsString(rule),
                    ATTENDANCE_RULE_CACHE_TTL
            );
        } catch (Exception ignored) {
        }
    }

    private void evictAttendanceRuleCache(String tenantCode) {
        try {
            stringRedisTemplate.delete(redisKeyBuilder.cache("tenant", "attendance-rule", tenantCode));
            stringRedisTemplate.opsForHash().delete(LEGACY_COMPANY_ATTENDANCE_RULE_KEY, tenantCode);
        } catch (Exception ignored) {
        }
    }

    private TenantAttendanceRule buildDefaultRule(String tenantCode) {
        TenantAttendanceRule rule = new TenantAttendanceRule();
        rule.setTenantCode(tenantCode);
        rule.setTenantName(tenantCode);
        rule.setStatus(CommonStatusEnum.ENABLED.getCode());
        rule.setRadius(DEFAULT_RADIUS);
        rule.setWorkStartTime(LocalTime.of(8, 30));
        rule.setWorkEndTime(LocalTime.of(9, 30));
        rule.setOffWorkStartTime(LocalTime.of(17, 30));
        rule.setOffWorkEndTime(LocalTime.of(18, 30));
        rule.setOverTimeStartTime(LocalTime.of(18, 30));
        rule.setOverTimeEndTime(LocalTime.of(22, 0));
        rule.setLateToleranceMinutes(5);
        rule.setEarlyToleranceMinutes(5);
        rule.setWorkDays("1,2,3,4,5,6");
        rule.setEnableWifi(BinaryFlagEnum.NO.getCode());
        return rule;
    }

    private void validateLocation(TenantLocationAddRequest request) {
        if (request == null || !isValidLatitude(request.getLatitude()) || !isValidLongitude(request.getLongitude())) {
            throw new BusinessException("定位坐标不合法，请重新获取当前位置");
        }
        if (request.getRadius() != null && (request.getRadius() <= 0D || request.getRadius() > MAX_RADIUS)) {
            throw new BusinessException("打卡半径需在 1 到 10000 米之间");
        }
    }

    private boolean isValidLatitude(Double value) {
        return value != null && value >= -90D && value <= 90D;
    }

    private boolean isValidLongitude(Double value) {
        return value != null && value >= -180D && value <= 180D;
    }

    private Double safeRadius(Double value) {
        return value == null ? DEFAULT_RADIUS : value;
    }

    private String clean(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}

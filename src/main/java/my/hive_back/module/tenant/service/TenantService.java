package my.hive_back.module.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive.common.redis.HiveRedisKeyBuilder;
import my.hive_back.module.attendance.mapper.EmployeeAttendanceLocationMapper;
import my.hive_back.module.tenant.mapper.TenantAttendanceLocationMapper;
import my.hive_back.module.tenant.mapper.TenantAttendanceRuleMapper;
import my.hive_back.module.tenant.model.entity.TenantAttendanceLocation;
import my.hive_back.module.tenant.model.entity.TenantAttendanceRule;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * TenantService belongs to the mini-program backend tenant module.
 *
 * <p>The mini-program only reads tenant attendance rules. Write operations for
 * company coordinates and attendance windows must go through the management
 * backend so changes are auditable and cannot be made from a mobile client.</p>
 */
@Service
public class TenantService {

    private static final Duration ATTENDANCE_RULE_CACHE_TTL = Duration.ofHours(6);

    @Resource
    private TenantAttendanceRuleMapper tenantLocationMapper;

    @Resource
    private TenantAttendanceLocationMapper tenantAttendanceLocationMapper;

    @Resource
    private EmployeeAttendanceLocationMapper employeeAttendanceLocationMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private HiveRedisKeyBuilder redisKeyBuilder;

    public TenantAttendanceRule getTenantLocation() {
        String tenantCode = TenantPermissionContext.getTenantCode();
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new BusinessException("没有权限或租户不存在");
        }

        TenantAttendanceRule tenantLocation = getCachedAttendanceRule(tenantCode);
        if (tenantLocation == null) {
            tenantLocation = tenantLocationMapper.selectOne(new LambdaQueryWrapper<TenantAttendanceRule>()
                    .eq(TenantAttendanceRule::getTenantCode, tenantCode)
                    .last("LIMIT 1"));
            cacheAttendanceRule(tenantCode, tenantLocation);
        }
        if (tenantLocation == null) {
            throw new BusinessException("租户考勤规则未配置，请联系管理员");
        }
        tenantLocation.setLocations(filterAssignedLocations(loadLocations(tenantCode), tenantCode, TenantPermissionContext.getUserId()));
        return tenantLocation;
    }

    private List<TenantAttendanceLocation> loadLocations(String tenantCode) {
        List<TenantAttendanceLocation> locations = tenantAttendanceLocationMapper.selectActiveByTenantCode(tenantCode);
        return locations == null ? List.of() : locations;
    }

    private List<TenantAttendanceLocation> filterAssignedLocations(List<TenantAttendanceLocation> locations, String tenantCode, Long userId) {
        if (userId == null) {
            return locations;
        }
        List<Long> assignedLocationIds = employeeAttendanceLocationMapper.selectLocationIds(tenantCode, userId);
        if (assignedLocationIds == null || assignedLocationIds.isEmpty()) {
            return locations;
        }
        Set<Long> assignedIdSet = assignedLocationIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        if (assignedIdSet.isEmpty()) {
            return locations;
        }
        return locations.stream()
                .filter(location -> location != null && assignedIdSet.contains(location.getId()))
                .toList();
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
}

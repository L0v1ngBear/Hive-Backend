package my.hive_back.module.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive.common.redis.HiveRedisKeyBuilder;
import my.hive.common.utils.TimeUtil;
import my.hive_back.common.enums.BinaryFlagEnum;
import my.hive_back.module.attendance.PunchStatusEnum;
import my.hive_back.module.attendance.mapper.AttendanceRecordMapper;
import my.hive_back.module.attendance.model.dto.AttendancePunchRequest;
import my.hive_back.module.attendance.model.entity.AttendanceRecord;
import my.hive_back.module.tenant.mapper.TenantAttendanceRuleMapper;
import my.hive_back.module.tenant.model.entity.TenantAttendanceRule;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
/**
 * AttendanceService 属于小程序后端考勤模块，实现核心业务编排与规则逻辑。
 */
@Service
public class AttendanceService {

    private static final String LEGACY_COMPANY_ATTENDANCE_RULE_KEY = "companyAttendanceRule";
    private static final Duration ATTENDANCE_RULE_CACHE_TTL = Duration.ofHours(6);
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = buildReleaseLockScript();

    @Resource
    private AttendanceRecordMapper attendanceRecordMapper;

    @Resource
    private TenantAttendanceRuleMapper tenantAttendanceRuleMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private HiveRedisKeyBuilder redisKeyBuilder;

    @Transactional(rollbackFor = Exception.class)
    public void punch(AttendancePunchRequest request) {
        String tenantCode = TenantPermissionContext.getTenantCode();
        Long userId = TenantPermissionContext.getUserId();
        LocalTime nowTime = TimeUtil.nowTime();
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String punchId = dateStr + "_" + userId;

        String lockKey = redisKeyBuilder.lock("attendance", "punch", tenantCode, punchId);
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, 10, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException("打卡正在处理中，请稍后再试");
        }
        try {
            doPunch(request, tenantCode, userId, nowTime, punchId);
        } finally {
            releaseLock(lockKey, lockValue);
        }
    }

    private void doPunch(AttendancePunchRequest request, String tenantCode, Long userId, LocalTime nowTime, String punchId) {
        TenantAttendanceRule rule = getCompanyAttendanceRule(tenantCode);
        if (!isWorkDay(rule, LocalDate.now())) {
            throw new BusinessException("今天不是考勤规则中的工作日，无需打卡");
        }

        Double distance = null;
        if (BinaryFlagEnum.YES.matches(rule.getEnableGps())) {
            // 基础校验：距离校验。管理端关闭 GPS 围栏后，小程序可不校验位置。
            distance = calculateDistance(rule.getLatitude(), rule.getLongitude(), request.getUserLat(), request.getUserLng());
            if (distance > safeRadius(rule.getRadius())) {
                throw new BusinessException("超出打卡范围");
            }
        }

        AttendanceRecord record = attendanceRecordMapper.selectOne(
                new LambdaQueryWrapper<AttendanceRecord>().eq(AttendanceRecord::getPunchId, punchId));

        if (record == null) {
            // 上班打卡
            if (nowTime.isBefore(rule.getWorkStartTime()) || nowTime.isAfter(rule.getWorkEndTime())) {
                throw new BusinessException("当前不在上班打卡时间段内");
            }
            record = new AttendanceRecord();
            record.setPunchId(punchId);
            record.setUserId(userId);
            record.setTenantCode(tenantCode);
            record.setSignInTime(nowTime);
            record.setSignInDistance(distance);
            // 仅做初步判定，最终由统计任务核准
            record.setSignInStatus(nowTime.isAfter(rule.getWorkStartTime().plusMinutes(nonNegative(rule.getLateToleranceMinutes()))) ?
                    PunchStatusEnum.LATE.getCode() : PunchStatusEnum.NORMAL.getCode());
            attendanceRecordMapper.insert(record);
        } else {
            // 下班打卡（覆盖更新，以最后一次为准）
            boolean inOffWorkWindow = !nowTime.isBefore(rule.getOffWorkStartTime()) && !nowTime.isAfter(rule.getOffWorkEndTime());
            boolean inOvertimeWindow = inTimeRange(nowTime, rule.getOverTimeStartTime(), rule.getOverTimeEndTime());
            if (!inOffWorkWindow && !inOvertimeWindow) {
                throw new BusinessException("当前不在下班或加班打卡时间段内");
            }
            record.setSignOutTime(nowTime);
            record.setSignOutDistance(distance);
            if (inOvertimeWindow) {
                record.setSignOutStatus(PunchStatusEnum.OVERTIME.getCode());
            } else {
                record.setSignOutStatus(nowTime.isBefore(rule.getOffWorkStartTime().minusMinutes(nonNegative(rule.getEarlyToleranceMinutes()))) ?
                        PunchStatusEnum.EARLY.getCode() : PunchStatusEnum.NORMAL.getCode());
            }
            attendanceRecordMapper.updateById(record);
        }
    }

    private void releaseLock(String lockKey, String lockValue) {
        stringRedisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(lockKey), lockValue);
    }

    /**
     * 获取公司考勤规则（缓存优先）
     */
    private TenantAttendanceRule getCompanyAttendanceRule(String tenantCode) {
        TenantAttendanceRule rule = getCachedAttendanceRule(tenantCode);

        if (rule == null || rule.getRadius() == null || rule.getWorkStartTime() == null || rule.getWorkEndTime() == null || rule.getOffWorkStartTime() == null || rule.getOffWorkEndTime() == null) {
            rule = tenantAttendanceRuleMapper.selectByTenantCode(tenantCode);
            if (rule == null) {
                throw new BusinessException("考勤配置异常：未找到所属公司的考勤规则");
            }
            cacheAttendanceRule(tenantCode, rule);
        }
        return rule;
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
        try {
            stringRedisTemplate.opsForValue().set(
                    redisKeyBuilder.cache("tenant", "attendance-rule", tenantCode),
                    objectMapper.writeValueAsString(rule),
                    ATTENDANCE_RULE_CACHE_TTL
            );
            stringRedisTemplate.opsForHash().delete(LEGACY_COMPANY_ATTENDANCE_RULE_KEY, tenantCode);
        } catch (Exception ignored) {
        }
    }

    /**
     * 计算球面距离（Haversine formula）
     */
    private Double calculateDistance(Double companyLat, Double companyLng, Double userLat, Double userLng) {
        if (!isValidLatitude(userLat) || !isValidLongitude(userLng)) {
            throw new BusinessException("定位坐标不合法，请重新获取当前位置");
        }
        if (!isValidLatitude(companyLat) || !isValidLongitude(companyLng)) {
            throw new BusinessException("考勤配置异常：请先设置公司打卡点");
        }

        double earthRadius = 6378137.0;
        double radLat1 = Math.toRadians(userLat);
        double radLat2 = Math.toRadians(companyLat);
        double a = radLat1 - radLat2;
        double b = Math.toRadians(userLng) - Math.toRadians(companyLng);
        double s = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin(a / 2), 2) +
                Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(b / 2), 2)));
        s = s * earthRadius;
        return s;
    }

    private double safeRadius(Double radius) {
        return radius == null || radius <= 0D ? 300D : radius;
    }

    private boolean isValidLatitude(Double value) {
        return value != null && value >= -90D && value <= 90D;
    }

    private boolean isValidLongitude(Double value) {
        return value != null && value >= -180D && value <= 180D;
    }

    public List<AttendanceRecord> selectRecord(Long userId) {
        return attendanceRecordMapper.selectList(
                new LambdaQueryWrapper<AttendanceRecord>()
                        .eq(AttendanceRecord::getUserId, userId)
                        .orderByDesc(AttendanceRecord::getId)
        );
    }

    private boolean isWorkDay(TenantAttendanceRule rule, LocalDate date) {
        if (rule.getWorkDays() == null || rule.getWorkDays().isBlank()) {
            return true;
        }
        int dayValue = date.getDayOfWeek().getValue();
        return Arrays.stream(rule.getWorkDays().split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .anyMatch(item -> String.valueOf(dayValue).equals(item));
    }

    private long nonNegative(Integer value) {
        return value == null || value < 0 ? 0L : value;
    }

    private boolean inTimeRange(LocalTime nowTime, LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) {
            return false;
        }
        return !nowTime.isBefore(startTime) && !nowTime.isAfter(endTime);
    }

    private static DefaultRedisScript<Long> buildReleaseLockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText("""
                if redis.call('get', KEYS[1]) == ARGV[1] then
                    return redis.call('del', KEYS[1])
                end
                return 0
                """);
        return script;
    }
}

package my.hive_back.module.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import my.hive_back.common.context.TenantPermissionContext;
import my.hive_back.common.exception.BusinessException;
import my.hive_back.common.utils.RedisUtil;
import my.hive_back.common.utils.TimeUtil;
import my.hive_back.module.attendance.PunchStatusEnum;
import my.hive_back.module.attendance.mapper.AttendanceRecordMapper;
import my.hive_back.module.attendance.model.dto.AttendancePunchRequest;
import my.hive_back.module.attendance.model.entity.AttendanceRecord;
import my.hive_back.module.tenant.mapper.TenantAttendanceRuleMapper;
import my.hive_back.module.tenant.model.entity.TenantAttendanceRule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Service
public class AttendanceService {

    private static final String COMPANY_ATTENDANCE_RULE_KEY = "companyAttendanceRule";

    @Resource
    private AttendanceRecordMapper attendanceRecordMapper;

    @Resource
    private TenantAttendanceRuleMapper tenantAttendanceRuleMapper;

    @Resource
    private RedisUtil redisUtil;

    @Transactional(rollbackFor = Exception.class)
    public void punch(AttendancePunchRequest request) {
        String tenantCode = TenantPermissionContext.getTenantCode();
        Long userId = TenantPermissionContext.getUserId();
        LocalTime nowTime = TimeUtil.nowTime();
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String punchId = dateStr + "_" + userId;

        TenantAttendanceRule rule = getCompanyAttendanceRule(tenantCode);

        // 基础校验：距离校验
        Double distance = calculateDistance(rule.getLatitude(), rule.getLongitude(), request.getUserLat(), request.getUserLng());
        if (distance > rule.getRadius()) {
            throw new BusinessException("超出打卡范围");
        }

        AttendanceRecord record = attendanceRecordMapper.selectOne(
                new LambdaQueryWrapper<AttendanceRecord>().eq(AttendanceRecord::getPunchId, punchId));

        if (record == null) {
            // 上班打卡
            record = new AttendanceRecord();
            record.setPunchId(punchId);
            record.setUserId(userId);
            record.setTenantCode(tenantCode);
            record.setSignInTime(nowTime);
            record.setSignInDistance(distance);
            // 仅做初步判定，最终由统计任务核准
            record.setSignInStatus(nowTime.isAfter(rule.getWorkStartTime()) ?
                    PunchStatusEnum.LATE.getCode() : PunchStatusEnum.NORMAL.getCode());
            attendanceRecordMapper.insert(record);
        } else {
            // 下班打卡（覆盖更新，以最后一次为准）
            record.setSignOutTime(nowTime);
            record.setSignOutDistance(distance);
            record.setSignOutStatus(nowTime.isBefore(rule.getWorkEndTime()) ?
                    PunchStatusEnum.EARLY.getCode() : PunchStatusEnum.NORMAL.getCode());
            attendanceRecordMapper.updateById(record);
        }
    }

    /**
     * 获取公司考勤规则（缓存优先）
     */
    private TenantAttendanceRule getCompanyAttendanceRule(String tenantCode) {
        TenantAttendanceRule rule = redisUtil.getHashValue(
                COMPANY_ATTENDANCE_RULE_KEY, tenantCode, TenantAttendanceRule.class);

        if (rule == null || rule.getRadius() == null || rule.getWorkStartTime() == null || rule.getWorkEndTime() == null || rule.getOffWorkStartTime() == null || rule.getOffWorkEndTime() == null) {
            rule = tenantAttendanceRuleMapper.selectByTenantCode(tenantCode);
            if (rule == null) {
                throw new BusinessException("考勤配置异常：未找到所属公司的考勤规则");
            }
            // TODO: 查询后记得回写缓存，例如 redisUtil.putHashValue(...)
            redisUtil.pushHashValue(COMPANY_ATTENDANCE_RULE_KEY, tenantCode, rule);
        }
        return rule;
    }

    /**
     * 计算球面距离（Haversine formula）
     */
    private Double calculateDistance(Double companyLat, Double companyLng, Double userLat, Double userLng) {
        if (userLat == null || userLng == null) {
            throw new IllegalArgumentException("定位失败：未获取到用户的经纬度");
        }
        if (companyLat == null || companyLng == null) {
            throw new IllegalArgumentException("考勤配置异常：未获取到公司经纬度");
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

    public AttendanceRecord selectRecord(Long userId) {
        return attendanceRecordMapper.selectOne(
                new LambdaQueryWrapper<AttendanceRecord>()
                        .eq(AttendanceRecord::getUserId, userId)
                        .orderByDesc(AttendanceRecord::getId)
        );
    }
}
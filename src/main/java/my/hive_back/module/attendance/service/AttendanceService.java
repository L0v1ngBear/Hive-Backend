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
import my.hive_back.module.tenant.mapper.TenantAttendanceInfoMapper;
import my.hive_back.module.tenant.model.entity.TenantAttendanceRule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Service
public class AttendanceService {

    private static final String COMPANY_ATTENDANCE_RULE_KEY = "companyAttendanceRule";

    @Resource
    private AttendanceRecordMapper attendanceRecordMapper;

    @Resource
    private TenantAttendanceInfoMapper tenantAttendanceInfoMapper;

    @Resource
    private RedisUtil redisUtil;

    @Transactional(rollbackFor = Exception.class)
    public void punch(AttendancePunchRequest request) {
        // 1. 获取上下文基础信息
        String tenantCode = TenantPermissionContext.getTenantCode();
        Long userId = TenantPermissionContext.getUserId();
        LocalTime nowTime = TimeUtil.nowTime();

        String punchId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "_" + userId;

        // 2. 获取公司考勤规则（包含位置与时间规则）
        TenantAttendanceRule rule = getCompanyAttendanceRule(tenantCode);

        // 3. 校验时间是否在允许打卡的整体开放时间段内
        if (rule.getWorkStartTime() != null && nowTime.isBefore(rule.getWorkStartTime())) {
            throw new BusinessException("打卡失败：未到今日开放打卡时间");
        }
        if (rule.getOverTimeEndTime() != null && nowTime.isAfter(rule.getOverTimeEndTime())) {
            throw new BusinessException("打卡失败：今日打卡通道已关闭");
        }

        // 4. 校验地理位置距离
        Double userLat = request.getUserLat();
        Double userLng = request.getUserLng();
        BigDecimal distance = calculateDistance(rule.getLatitude(), rule.getLongitude(), userLat, userLng);
        BigDecimal validRadius = BigDecimal.valueOf(rule.getRadius()).setScale(2, RoundingMode.HALF_UP);

        if (distance.compareTo(validRadius) > 0) {
            throw new RuntimeException("打卡失败：您当前距离公司 " + distance + " 米，超出了允许范围（" + validRadius + "米）");
        }

        // 5. 查询今日是否已有打卡记录
        AttendanceRecord existingRecord = attendanceRecordMapper.selectOne(
                new LambdaQueryWrapper<AttendanceRecord>()
                        .eq(AttendanceRecord::getPunchId, punchId)
        );

        if (existingRecord == null) {
            // ============================
            // 场景 A: 没有记录 -> 首次打卡（上班）
            // ============================
            AttendanceRecord newRecord = new AttendanceRecord();
            newRecord.setPunchId(punchId);
            newRecord.setUserId(userId);
            newRecord.setTenantCode(tenantCode);

            // 专属字段：记录上班信息
            newRecord.setSignInTime(nowTime);
            newRecord.setSignInLat(userLat);
            newRecord.setSignInLng(userLng);

            // 修正：判断是否迟到（与规定的上班时间 rule.getWorkStartTime() 比较）
            if (rule.getWorkStartTime() != null && nowTime.isAfter(rule.getWorkStartTime())) {
                newRecord.setSignInStatus(PunchStatusEnum.LATE.getCode()); // 迟到
            } else {
                newRecord.setSignInStatus(PunchStatusEnum.NORMAL.getCode()); // 正常上班
            }

            // 插入新记录
            attendanceRecordMapper.insert(newRecord);

        } else {
            // ============================
            // 场景 B: 已有记录 -> 再次打卡（更新为下班/加班）
            // ============================
            // 专属字段：记录下班信息（多次打卡会覆盖最新的下班时间，符合实际业务）
            existingRecord.setSignOutTime(nowTime);
            existingRecord.setSignOutLat(userLat);
            existingRecord.setSignOutLng(userLng);

            // 判断下班状态
            if (rule.getWorkEndTime() != null && nowTime.isAfter(rule.getWorkEndTime())) {
                existingRecord.setSignOutStatus(PunchStatusEnum.OVERTIME.getCode()); // 加班
            } else if (rule.getOffWorkStartTime() != null && nowTime.isBefore(rule.getOffWorkStartTime())) {
                existingRecord.setSignOutStatus(PunchStatusEnum.EARLY.getCode()); // 早退
            } else {
                existingRecord.setSignOutStatus(PunchStatusEnum.NORMAL.getCode()); // 正常下班
            }

            // 修复：这里必须是 updateById，不能用 insert！
            attendanceRecordMapper.updateById(existingRecord);
        }
    }

    /**
     * 获取公司考勤规则（缓存优先）
     */
    private TenantAttendanceRule getCompanyAttendanceRule(String tenantCode) {
        TenantAttendanceRule rule = redisUtil.getHashValueAndConvert(
                COMPANY_ATTENDANCE_RULE_KEY, tenantCode, TenantAttendanceRule.class, null);

        if (rule == null || rule.getRadius() == null || rule.getWorkStartTime() == null || rule.getWorkEndTime() == null || rule.getOffWorkStartTime() == null || rule.getOffWorkEndTime() == null) {
            rule = tenantAttendanceInfoMapper.selectOne(
                    new LambdaQueryWrapper<TenantAttendanceRule>()
                            .eq(TenantAttendanceRule::getTenantCode, tenantCode)
            );
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
    private BigDecimal calculateDistance(Double companyLat, Double companyLng, Double userLat, Double userLng) {
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
        return BigDecimal.valueOf(s).setScale(2, RoundingMode.HALF_UP);
    }

    public AttendanceRecord selectRecord(Long userId) {
        return attendanceRecordMapper.selectOne(
                new LambdaQueryWrapper<AttendanceRecord>()
                        .eq(AttendanceRecord::getUserId, userId)
                        .orderByDesc(AttendanceRecord::getId)
        );
    }
}
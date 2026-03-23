package my.hive_back.common.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import my.hive_back.module.attendance.PunchStatusEnum;
import my.hive_back.module.attendance.mapper.AttendanceRecordMapper;
import my.hive_back.module.attendance.model.entity.AttendanceRecord;
import my.hive_back.module.leave.LeaveStatusEnum;
import my.hive_back.module.leave.mapper.LeaveMapper;
import my.hive_back.module.leave.model.entity.UserLeave;
import my.hive_back.module.tenant.mapper.TenantAttendanceRuleMapper;
import my.hive_back.module.tenant.model.entity.TenantAttendanceRule;
import my.hive_back.module.user.mapper.UserMapper;
import my.hive_back.module.user.model.entity.User;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 考勤统计定时任务
 * 核心逻辑：每日凌晨核算昨日未打卡或打卡异常的情况，结合请假单进行状态校准
 */
@Slf4j
@Component
public class AttendanceStaticsScheduler {

    @Resource
    private AttendanceRecordMapper recordMapper;

    @Resource
    private LeaveMapper leaveMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private TenantAttendanceRuleMapper ruleMapper;

    /**
     * 每天凌晨2点核算昨日考勤
     * 补充逻辑：处理那些完全没打卡（表中无记录）或打卡后状态仍为异常的用户
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void statisticsYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        String dateStr = yesterday.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        log.info("开始核算昨日 ({}) 考勤数据...", dateStr);

        // 1. 获取所有在职用户
        List<User> userList = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getStatus, 1));

        // 2. 获取所有租户的考勤规则 (Map存储提高查询效率)
        List<TenantAttendanceRule> allRules = ruleMapper.selectList(new LambdaQueryWrapper<>());
        Map<String, TenantAttendanceRule> ruleMap = allRules.stream()
                .collect(Collectors.toMap(TenantAttendanceRule::getTenantCode, r -> r));

        // 3. 获取昨日所有已通过的请假单
        List<UserLeave> allLeaves = leaveMapper.selectList(new LambdaQueryWrapper<UserLeave>()
                .eq(UserLeave::getStatus, LeaveStatusEnum.APPROVED.getCode())
//                .lt(UserLeave::getStartTime, yesterday.plusDays(1).atStartOfDay())
                .gt(UserLeave::getEndTime, yesterday.atStartOfDay()));
        Map<Long, List<UserLeave>> userLeaveMap = allLeaves.stream()
                .collect(Collectors.groupingBy(UserLeave::getApplyUserId));

        // 4. 获取昨日已有的打卡记录
        List<AttendanceRecord> existingRecords = recordMapper.selectList(new LambdaQueryWrapper<AttendanceRecord>()
                .likeRight(AttendanceRecord::getPunchId, dateStr));
        Map<Long, AttendanceRecord> userRecordMap = existingRecords.stream()
                .collect(Collectors.toMap(AttendanceRecord::getUserId, r -> r));

        // 5. 遍历用户进行判定
        for (User user : userList) {
            TenantAttendanceRule rule = ruleMap.get(user.getTenantCode());
            if (rule == null) continue;

            AttendanceRecord record = userRecordMap.get(user.getId());
            List<UserLeave> userLeaves = userLeaveMap.getOrDefault(user.getId(), List.of());

            processUserDayStatus(user, yesterday, dateStr, record, userLeaves, rule);
        }

        log.info("昨日考勤核算完成。");
    }

    private void processUserDayStatus(User user, LocalDate yesterday, String dateStr,
                                      AttendanceRecord record, List<UserLeave> leaves,
                                      TenantAttendanceRule rule) {

        boolean isNewRecord = false;
        if (record == null) {
            // A. 场景：用户昨日完全没打卡，需创建初始记录判定为“缺勤”或“请假”
            record = new AttendanceRecord();
            record.setPunchId(dateStr + "_" + user.getId());
            record.setUserId(user.getId());
            record.setTenantCode(user.getTenantCode());
            isNewRecord = true;
        }

        // --- 1. 上班状态校准 ---
        if (record.getSignInTime() == null) {
            // 没打卡：检查上班时间点是否被请假覆盖
            if (isTimeCoveredByLeaves(rule.getWorkStartTime(), yesterday, leaves)) {
                record.setSignInStatus(PunchStatusEnum.LEAVE.getCode());
            } else {
                record.setSignInStatus(PunchStatusEnum.ABSENT.getCode()); // 设为缺勤/缺卡
            }
        } else if (PunchStatusEnum.LATE.getCode().equals(record.getSignInStatus())) {
            // 迟到：检查规定的上班时间点是否在请假范围内（例如请假半天）
            if (isTimeCoveredByLeaves(rule.getWorkStartTime(), yesterday, leaves)) {
                record.setSignInStatus(PunchStatusEnum.LEAVE.getCode());
            }
        }

        // --- 2. 下班状态校准 ---
        if (record.getSignOutTime() == null) {
            // 没打卡：检查下班时间点是否被请假覆盖
            if (isTimeCoveredByLeaves(rule.getWorkEndTime(), yesterday, leaves)) {
                record.setSignOutStatus(PunchStatusEnum.LEAVE.getCode());
            } else {
                record.setSignOutStatus(PunchStatusEnum.ABSENT.getCode());
            }
        } else if (PunchStatusEnum.EARLY.getCode().equals(record.getSignOutStatus())) {
            // 早退：检查下班时间点是否在请假范围内
            if (isTimeCoveredByLeaves(rule.getWorkEndTime(), yesterday, leaves)) {
                record.setSignOutStatus(PunchStatusEnum.LEAVE.getCode());
            }
        }

        // --- 3. 持久化 ---
        if (isNewRecord) {
            recordMapper.insert(record);
        } else {
            recordMapper.updateById(record);
        }
    }

    /**
     * 判定特定时间点是否被请假单覆盖
     */
    private boolean isTimeCoveredByLeaves(LocalTime checkTime, LocalDate date, List<UserLeave> leaves) {
        if (checkTime == null) return false;
        LocalDateTime checkDateTime = LocalDateTime.of(date, checkTime);
        return leaves.stream().anyMatch(leave ->
                !checkDateTime.isBefore(leave.getStartTime()) && !checkDateTime.isAfter(leave.getEndTime()));
    }
}
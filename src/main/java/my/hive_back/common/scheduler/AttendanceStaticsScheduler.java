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
import my.hive_back.module.statics.attendance.mapper.AttendanceStaticsMapper;
import my.hive_back.module.statics.attendance.model.AttendanceStatics;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    @Resource
    private AttendanceStaticsMapper attendanceStaticsMapper;

    /**
     * 每天凌晨2点执行，统计昨日数据
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void statisticsYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("开始执行 {} 考勤统计任务", yesterday);

        // 1. 获取所有有效的租户考勤规则
        List<TenantAttendanceRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<>());
        Map<String, TenantAttendanceRule> ruleMap = rules.stream()
                .collect(Collectors.toMap(TenantAttendanceRule::getTenantCode, r -> r));

        // 2. 获取所有在职用户
        List<User> allUsers = userMapper.selectList(new LambdaQueryWrapper<User>().eq(User::getStatus, 1));

        // 3. 批量查询昨日所有已通过的请假单 (优化：一次性查出，内存过滤)
        List<UserLeave> allLeaves = leaveMapper.selectList(new LambdaQueryWrapper<UserLeave>()
                .eq(UserLeave::getStatus, LeaveStatusEnum.APPROVED.getCode())
                .lt(UserLeave::getStartTime, yesterday.plusDays(1).atStartOfDay())
                .gt(UserLeave::getEndTime, yesterday.atStartOfDay()));
        Map<Long, List<UserLeave>> userLeaveMap = allLeaves.stream()
                .collect(Collectors.groupingBy(UserLeave::getApplyUserId));

        // 4. 批量查询昨日打卡记录
        List<AttendanceRecord> allRecords = recordMapper.selectList(new LambdaQueryWrapper<AttendanceRecord>()
                .eq(AttendanceRecord::getCreateTime, yesterday));
        Map<Long, AttendanceRecord> userRecordMap = allRecords.stream()
                .collect(Collectors.toMap(AttendanceRecord::getUserId, r -> r));

        // 5. 遍历用户进行判定
        for (User user : allUsers) {
            TenantAttendanceRule rule = ruleMap.get(user.getTenantCode());
            if (rule == null) continue; // 无规则不统计

            AttendanceRecord punch = userRecordMap.get(user.getId());
            List<UserLeave> userLeaves = userLeaveMap.getOrDefault(user.getId(), List.of());

            AttendanceStatics statics = processUserDayStatus(user, yesterday, punch, userLeaves, rule);

            // 6. 保存或更新统计结果
            saveOrUpdateStatics(statics);
        }
        log.info("{} 考勤统计任务完成", yesterday);
    }

    private AttendanceStatics processUserDayStatus(User user, LocalDate date, AttendanceRecord punch,
                                                   List<UserLeave> leaves, TenantAttendanceRule rule) {
        AttendanceStatics statics = new AttendanceStatics();
        statics.setUserId(user.getId());
        statics.setTenantCode(user.getTenantCode());
        statics.setStatisticsDate(date);

        // 默认初始化状态
        int finalStatus = PunchStatusEnum.NORMAL.getCode();

        // --- A. 上班判定 ---
        LocalTime workStart = rule.getWorkStartTime();
        boolean isSignInNormal = false;

        if (punch == null || punch.getSignInTime() == null) {
            // 没打卡：检查是否被请假覆盖
            if (isTimeCoveredByLeaves(workStart, date, leaves)) {
                statics.set(PunchStatusEnum.LEAVE.getCode());
            } else {
                statics.setSignInStatus(PunchStatusEnum.ABSENT.getCode());
                finalStatus = PunchStatusEnum.ABSENT.getCode();
            }
        } else {
            // 已打卡：检查是否迟到
            if (punch.getSignInTime().toLocalTime().isAfter(workStart)) {
                // 迟到了：检查迟到时间点是否在请假范围内（例如请假半天）
                if (isTimeCoveredByLeaves(workStart, date, leaves)) {
                    statics.setSignInStatus(PunchStatusEnum.LEAVE_COMPENSATED.getCode());
                } else {
                    statics.setSignInStatus(PunchStatusEnum.LATE.getCode());
                    finalStatus = PunchStatusEnum.ABNORMAL.getCode();
                }
            } else {
                statics.setSignInStatus(PunchStatusEnum.NORMAL.getCode());
                isSignInNormal = true;
            }
        }

        // --- B. 下班判定 ---
        LocalTime workEnd = rule.getWorkEndTime();
        if (punch == null || punch.getSignOutTime() == null) {
            if (isTimeCoveredByLeaves(workEnd, date, leaves)) {
                statics.setSignOutStatus(PunchStatusEnum.LEAVE.getCode());
            } else {
                statics.setSignOutStatus(PunchStatusEnum.ABSENT.getCode());
                finalStatus = PunchStatusEnum.ABSENT.getCode();
            }
        } else {
            if (punch.getSignOutTime().toLocalTime().isBefore(workEnd)) {
                if (isTimeCoveredByLeaves(workEnd, date, leaves)) {
                    statics.setSignOutStatus(PunchStatusEnum.LEAVE_COMPENSATED.getCode());
                } else {
                    statics.setSignOutStatus(PunchStatusEnum.EARLY.getCode());
                    finalStatus = PunchStatusEnum.ABNORMAL.getCode();
                }
            } else {
                statics.setSignOutStatus(PunchStatusEnum.NORMAL.getCode());
            }
        }

        statics.setFinalStatus(finalStatus);
        return statics;
    }

    private boolean isTimeCoveredByLeaves(LocalTime checkTime, LocalDate date, List<UserLeave> leaves) {
        LocalDateTime checkDateTime = LocalDateTime.of(date, checkTime);
        return leaves.stream().anyMatch(leave ->
                !checkDateTime.isBefore(leave.getStartTime()) && !checkDateTime.isAfter(leave.getEndTime()));
    }

    private void saveOrUpdateStatics(AttendanceStatics statics) {
        // 先检查是否存在，存在则更新，不存在则插入
        AttendanceStatics exist = attendanceStaticsMapper.selectOne(new LambdaQueryWrapper<AttendanceStatics>()
                .eq(AttendanceStatics::getUserId, statics.getUserId())
                .eq(AttendanceStatics::getStaticsDate, statics.getStaticsDate()));
        if (exist != null) {
            statics.setId(exist.getId());
            attendanceStaticsMapper.updateById(statics);
        } else {
            attendanceStaticsMapper.insert(statics);
        }
    }
}
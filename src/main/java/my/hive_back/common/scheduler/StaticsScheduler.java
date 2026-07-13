package my.hive_back.common.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import my.hive.common.event.SystemEvent;
import my.hive.common.event.SystemEventPublisher;
import my.hive_back.module.attendance.PunchStatusEnum;
import my.hive_back.module.attendance.mapper.AttendanceRecordMapper;
import my.hive_back.module.attendance.model.entity.AttendanceRecord;
import my.hive_back.module.inventory.InventoryRecordOperateTypeEnum;
import my.hive_back.module.inventory.mapper.InventoryRecordMapper;
import my.hive_back.module.inventory.model.vo.InventoryDailyMetersVO;
import my.hive_back.module.leave.LeaveStatusEnum;
import my.hive_back.module.leave.mapper.LeaveMapper;
import my.hive_back.module.leave.model.entity.UserLeave;
import my.hive_back.module.statics.inventory.mapper.InventoryTrendStaticsMapper;
import my.hive_back.module.statics.inventory.model.entity.InventoryTrendStatics;
import my.hive_back.module.statics.inventory.service.InventoryTrendStaticsService;
import my.hive_back.module.tenant.mapper.TenantAttendanceRuleMapper;
import my.hive_back.module.tenant.mapper.TenantMapper;
import my.hive_back.module.tenant.model.entity.Tenant;
import my.hive_back.module.tenant.model.entity.TenantAttendanceRule;
import my.hive_back.module.user.mapper.UserMapper;
import my.hive_back.module.user.model.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class StaticsScheduler {

    private static final String ATTENDANCE_STAT_LOCK_KEY = "scheduler:attendance:daily";
    private static final String INVENTORY_STAT_LOCK_KEY = "scheduler:inventory:daily";

    @Resource
    private AttendanceRecordMapper recordMapper;

    @Resource
    private LeaveMapper leaveMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private TenantAttendanceRuleMapper ruleMapper;

    @Resource
    private InventoryRecordMapper inventoryRecordMapper;

    @Resource
    private TenantMapper tenantMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private InventoryTrendStaticsMapper staticsMapper;

    @Resource
    private InventoryTrendStaticsService staticsService;

    @Resource
    private SystemEventPublisher systemEventPublisher;

    @XxlJob("attendanceDailyStatJob")
    public void statisticsYesterday() {
        String lockValue = tryRunWithLock(ATTENDANCE_STAT_LOCK_KEY, 30 * 60);
        if (lockValue == null) {
            log.info("attendance daily stat skipped: another node is running");
            XxlJobHelper.log("attendance daily stat skipped: lock busy");
            return;
        }

        try {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            String dateStr = yesterday.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            log.info("attendance daily stat started, date={}", dateStr);
            XxlJobHelper.log("attendance daily stat started, date={}", dateStr);

            List<User> userList = userMapper.selectList(new LambdaQueryWrapper<User>()
                    .eq(User::getStatus, 1)
                    .and(wrapper -> wrapper.isNull(User::getAttendanceRequired)
                            .or()
                            .eq(User::getAttendanceRequired, 1)));
            if (userList == null || userList.isEmpty()) {
                XxlJobHelper.log("attendance daily stat finished: no active users");
                systemEventPublisher.info("ATTENDANCE_DAILY_STAT", "考勤日统计完成", "没有需要统计的在职员工",
                        Map.of("date", dateStr, "processed", 0));
                return;
            }

            List<TenantAttendanceRule> allRules = ruleMapper.selectList(new LambdaQueryWrapper<TenantAttendanceRule>()
                    .eq(TenantAttendanceRule::getStatus, 1));
            Map<String, TenantAttendanceRule> ruleMap = (allRules == null ? List.<TenantAttendanceRule>of() : allRules)
                    .stream()
                    .filter(rule -> rule != null && rule.getTenantCode() != null)
                    .collect(Collectors.toMap(TenantAttendanceRule::getTenantCode, rule -> rule, (left, right) -> left));

            LocalDateTime dayStart = yesterday.atStartOfDay();
            LocalDateTime dayEnd = yesterday.plusDays(1).atStartOfDay();
            List<UserLeave> allLeaves = leaveMapper.selectList(new LambdaQueryWrapper<UserLeave>()
                    .eq(UserLeave::getStatus, LeaveStatusEnum.APPROVED.getCode())
                    .gt(UserLeave::getEndTime, dayStart)
                    .lt(UserLeave::getStartTime, dayEnd));
            Map<Long, List<UserLeave>> userLeaveMap = (allLeaves == null ? List.<UserLeave>of() : allLeaves)
                    .stream()
                    .filter(leave -> leave != null && leave.getApplyUserId() != null)
                    .collect(Collectors.groupingBy(UserLeave::getApplyUserId));

            List<AttendanceRecord> existingRecords = recordMapper.selectList(new LambdaQueryWrapper<AttendanceRecord>()
                    .likeRight(AttendanceRecord::getPunchId, dateStr));
            Map<Long, AttendanceRecord> userRecordMap = (existingRecords == null ? List.<AttendanceRecord>of() : existingRecords)
                    .stream()
                    .filter(record -> record != null && record.getUserId() != null)
                    .collect(Collectors.toMap(AttendanceRecord::getUserId, record -> record, (left, right) -> left));

            int processed = 0;
            for (User user : userList) {
                if (user == null || user.getId() == null || user.getTenantCode() == null) {
                    continue;
                }
                TenantAttendanceRule rule = ruleMap.get(user.getTenantCode());
                if (rule == null) {
                    continue;
                }
                AttendanceRecord record = userRecordMap.get(user.getId());
                List<UserLeave> userLeaves = userLeaveMap.getOrDefault(user.getId(), List.of());
                processUserDayStatus(user, yesterday, dateStr, record, userLeaves, rule);
                processed++;
            }

            log.info("attendance daily stat finished, date={}, processed={}", dateStr, processed);
            XxlJobHelper.log("attendance daily stat finished, date={}, processed={}", dateStr, processed);
            systemEventPublisher.publish(SystemEvent.builder()
                    .eventType("ATTENDANCE_DAILY_STAT")
                    .level("INFO")
                    .module("attendance")
                    .title("考勤日统计完成")
                    .content("统计日期: " + dateStr + ", 处理员工数: " + processed)
                    .bizType("xxl-job")
                    .bizNo("attendanceDailyStatJob")
                    .detail(Map.of("date", dateStr, "processed", processed))
                    .build());
        } catch (Exception ex) {
            log.error("attendance daily stat failed", ex);
            XxlJobHelper.log("attendance daily stat failed: {}", ex.getMessage());
            systemEventPublisher.error("ATTENDANCE_DAILY_STAT_FAILED", "考勤日统计失败", ex,
                    Map.of("job", "attendanceDailyStatJob"));
            XxlJobHelper.handleFail(ex.getMessage());
        } finally {
            releaseLock(ATTENDANCE_STAT_LOCK_KEY, lockValue);
        }
    }

    private void processUserDayStatus(User user, LocalDate yesterday, String dateStr,
                                      AttendanceRecord record, List<UserLeave> leaves,
                                      TenantAttendanceRule rule) {
        boolean isNewRecord = false;
        if (record == null) {
            record = new AttendanceRecord();
            record.setPunchId(dateStr + "_" + user.getId());
            record.setUserId(user.getId());
            record.setTenantCode(user.getTenantCode());
            isNewRecord = true;
        }

        if (record.getSignInTime() == null) {
            if (isTimeCoveredByLeaves(rule.getWorkStartTime(), yesterday, leaves)) {
                record.setSignInStatus(PunchStatusEnum.LEAVE.getCode());
            } else {
                record.setSignInStatus(PunchStatusEnum.ABSENT.getCode());
            }
        } else if (PunchStatusEnum.LATE.getCode().equals(record.getSignInStatus())
                && isTimeCoveredByLeaves(rule.getWorkStartTime(), yesterday, leaves)) {
            record.setSignInStatus(PunchStatusEnum.LEAVE.getCode());
        }

        if (record.getSignOutTime() == null) {
            if (isTimeCoveredByLeaves(rule.getOffWorkStartTime(), yesterday, leaves)
                    || isTimeCoveredByLeaves(rule.getOffWorkEndTime(), yesterday, leaves)) {
                record.setSignOutStatus(PunchStatusEnum.LEAVE.getCode());
            } else {
                record.setSignOutStatus(PunchStatusEnum.ABSENT.getCode());
            }
        } else if (PunchStatusEnum.EARLY.getCode().equals(record.getSignOutStatus())
                && (isTimeCoveredByLeaves(rule.getOffWorkStartTime(), yesterday, leaves)
                || isTimeCoveredByLeaves(rule.getOffWorkEndTime(), yesterday, leaves))) {
            record.setSignOutStatus(PunchStatusEnum.LEAVE.getCode());
        }

        if (isNewRecord) {
            recordMapper.insert(record);
        } else {
            recordMapper.updateById(record);
        }
    }

    private boolean isTimeCoveredByLeaves(LocalTime checkTime, LocalDate date, List<UserLeave> leaves) {
        if (checkTime == null || leaves == null || leaves.isEmpty()) {
            return false;
        }
        LocalDateTime checkDateTime = LocalDateTime.of(date, checkTime);
        return leaves.stream().anyMatch(leave ->
                leave != null
                        && leave.getStartTime() != null
                        && leave.getEndTime() != null
                        && !checkDateTime.isBefore(leave.getStartTime())
                        && !checkDateTime.isAfter(leave.getEndTime()));
    }

    @XxlJob("inventoryDailyStatJob")
    public void inventoryDailyStatTask() {
        String lockValue = tryRunWithLock(INVENTORY_STAT_LOCK_KEY, 20 * 60);
        if (lockValue == null) {
            log.info("inventory daily stat skipped: another node is running");
            XxlJobHelper.log("inventory daily stat skipped: lock busy");
            return;
        }

        try {
            LocalDate statDate = LocalDate.now().minusDays(1);
            XxlJobHelper.log("inventory daily stat started, date={}", statDate);

            List<String> tenantCodes = tenantMapper.selectList(new LambdaQueryWrapper<Tenant>()
                            .eq(Tenant::getDeleted, 0)
                            .eq(Tenant::getStatus, 1)
                            .select(Tenant::getTenantCode))
                    .stream()
                    .map(Tenant::getTenantCode)
                    .filter(tenantCode -> tenantCode != null && !tenantCode.isBlank())
                    .toList();

            if (tenantCodes.isEmpty()) {
                log.info("inventory daily stat finished: no enabled tenant");
                XxlJobHelper.log("inventory daily stat finished: no enabled tenant");
                systemEventPublisher.info("INVENTORY_DAILY_STAT", "库存日统计完成", "没有启用中的租户",
                        Map.of("date", statDate.toString(), "tenantCount", 0));
                return;
            }

            List<InventoryTrendStatics> insertList = new ArrayList<>();
            List<InventoryTrendStatics> updateList = new ArrayList<>();
            LocalDateTime statDateTime = statDate.atStartOfDay();
            List<InventoryTrendStatics> existingStats = staticsMapper.selectList(new LambdaQueryWrapper<InventoryTrendStatics>()
                    .eq(InventoryTrendStatics::getStatDate, statDateTime)
                    .in(InventoryTrendStatics::getTenantCode, tenantCodes));
            Map<String, InventoryTrendStatics> existingStatMap = (existingStats == null ? List.<InventoryTrendStatics>of() : existingStats)
                    .stream()
                    .filter(stat -> stat != null && stat.getTenantCode() != null)
                    .collect(Collectors.toMap(InventoryTrendStatics::getTenantCode, stat -> stat, (left, right) -> left));
            int failedTenantCount = 0;
            LocalDateTime dayStart = statDate.atStartOfDay();
            LocalDateTime dayEnd = statDate.plusDays(1).atStartOfDay();

            for (String tenantCode : tenantCodes) {
                try {
                    InventoryDailyMetersVO dailyMeters = inventoryRecordMapper.sumDailyMeters(
                            tenantCode,
                            InventoryRecordOperateTypeEnum.IN.getCode(),
                            InventoryRecordOperateTypeEnum.EXTERNAL_IMPORT.getCode(),
                            InventoryRecordOperateTypeEnum.OUT.getCode(),
                            dayStart,
                            dayEnd
                    );
                    Float totalIn = toMetersFloat(dailyMeters == null ? null : dailyMeters.getInMeters());
                    Float totalOut = toMetersFloat(dailyMeters == null ? null : dailyMeters.getOutMeters());

                    InventoryTrendStatics stat = new InventoryTrendStatics();
                    stat.setStatDate(statDateTime);
                    stat.setTenantCode(tenantCode);
                    stat.setDayInMeters(totalIn);
                    stat.setDayOutMeters(totalOut);
                    stat.setUpdateTime(LocalDateTime.now());

                    InventoryTrendStatics exist = existingStatMap.get(tenantCode);
                    if (exist != null) {
                        stat.setId(exist.getId());
                        updateList.add(stat);
                    } else {
                        stat.setCreateTime(LocalDateTime.now());
                        insertList.add(stat);
                    }
                    log.info("inventory daily stat tenant finished, tenantCode={}, in={}, out={}", tenantCode, totalIn, totalOut);
                } catch (Exception ex) {
                    failedTenantCount++;
                    log.error("inventory daily stat tenant failed, tenantCode={}", tenantCode, ex);
                    XxlJobHelper.log("inventory daily stat tenant failed, tenantCode={}, error={}", tenantCode, ex.getMessage());
                }
            }

            if (!insertList.isEmpty()) {
                staticsService.saveBatch(insertList);
            }
            if (!updateList.isEmpty()) {
                staticsService.updateBatchById(updateList);
            }

            log.info("inventory daily stat finished, date={}, tenantCount={}", statDate, tenantCodes.size());
            XxlJobHelper.log("inventory daily stat finished, date={}, tenantCount={}, failedTenantCount={}",
                    statDate, tenantCodes.size(), failedTenantCount);
            systemEventPublisher.publish(SystemEvent.builder()
                    .eventType("INVENTORY_DAILY_STAT")
                    .level(failedTenantCount > 0 ? "WARN" : "INFO")
                    .module("inventory")
                    .title(failedTenantCount > 0 ? "库存日统计存在失败租户" : "库存日统计完成")
                    .content("统计日期: " + statDate + ", 租户数: " + tenantCodes.size() + ", 失败租户数: " + failedTenantCount)
                    .bizType("xxl-job")
                    .bizNo("inventoryDailyStatJob")
                    .detail(Map.of(
                            "date", statDate.toString(),
                            "tenantCount", tenantCodes.size(),
                            "failedTenantCount", failedTenantCount,
                            "insertCount", insertList.size(),
                            "updateCount", updateList.size()
                    ))
                    .build());
        } catch (Exception ex) {
            log.error("inventory daily stat failed", ex);
            XxlJobHelper.log("inventory daily stat failed: {}", ex.getMessage());
            systemEventPublisher.error("INVENTORY_DAILY_STAT_FAILED", "库存日统计失败", ex,
                    Map.of("job", "inventoryDailyStatJob"));
            XxlJobHelper.handleFail(ex.getMessage());
        } finally {
            releaseLock(INVENTORY_STAT_LOCK_KEY, lockValue);
        }
    }

    private Float toMetersFloat(BigDecimal value) {
        return value == null ? 0f : value.floatValue();
    }

    private String tryRunWithLock(String lockKey, long expireSeconds) {
        String lockValue = UUID.randomUUID().toString();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, expireSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success) ? lockValue : null;
    }

    private void releaseLock(String lockKey, String lockValue) {
        try {
            String currentValue = stringRedisTemplate.opsForValue().get(lockKey);
            if (lockValue != null && lockValue.equals(currentValue)) {
                stringRedisTemplate.delete(lockKey);
            }
        } catch (Exception ex) {
            log.warn("release scheduler lock failed, lockKey={}", lockKey, ex);
        }
    }
}

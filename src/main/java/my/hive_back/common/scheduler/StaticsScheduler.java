package my.hive_back.common.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import my.hive.common.context.TenantPermissionContext;
import my.hive_back.common.utils.RedisUtil;
import my.hive_back.module.attendance.PunchStatusEnum;
import my.hive_back.module.attendance.mapper.AttendanceRecordMapper;
import my.hive_back.module.attendance.model.entity.AttendanceRecord;
import my.hive_back.module.leave.LeaveStatusEnum;
import my.hive_back.module.leave.mapper.LeaveMapper;
import my.hive_back.module.leave.model.entity.UserLeave;
import my.hive_back.module.statics.inventory.mapper.InventoryTrendStaticsMapper;
import my.hive_back.module.statics.inventory.model.entity.InventoryTrendStatics;
import my.hive_back.module.statics.inventory.service.InventoryTrendStaticsService;
import my.hive_back.module.tenant.mapper.TenantAttendanceRuleMapper;
import my.hive_back.module.tenant.mapper.TenantMapper;
import my.hive_back.module.tenant.model.entity.TenantAttendanceRule;
import my.hive_back.module.user.mapper.UserMapper;
import my.hive_back.module.user.model.entity.User;
import my.hive_back.module.tenant.model.entity.Tenant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * StaticsScheduler 属于小程序后端通用能力层，承载定时调度相关逻辑。
 */
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

    @Value("${redis.key-prefix.trend.today_in}")
    private String REDIS_TODAY_IN;

    @Value("${redis.key-prefix.trend.today_out}")
    private String REDIS_TODAY_OUT;

    @Resource
    private TenantMapper tenantMapper;

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private InventoryTrendStaticsMapper staticsMapper;

    @Resource
    private InventoryTrendStaticsService staticsService;

    /**
     * 每天凌晨2点核算昨日考勤
     * 补充逻辑：处理那些完全没打卡（表中无记录）或打卡后状态仍为异常的用户
     */
    @Scheduled(cron = "${scheduler.attendance.daily-cron:0 0 2 * * ?}")
    public void statisticsYesterday() {
        String attendanceLockValue = tryRunWithLock(ATTENDANCE_STAT_LOCK_KEY, 30 * 60);
        if (attendanceLockValue == null) {
            log.info("昨日考勤核算任务已在其他节点执行，本次跳过");
            return;
        }

        // 【关键】：开启忽略多租户插件，让接下来的所有查询在全表进行
        try {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            String dateStr = yesterday.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            log.info("开始核算所有租户昨日 ({}) 考勤数据...", dateStr);

            // 1. 获取所有在职用户 (此时 selectList 不会自动拼接 AND tenant_code = ?)
            List<User> userList = userMapper.selectList(new LambdaQueryWrapper<User>()
                    .eq(User::getStatus, 1));

            // 2. 获取所有租户的考勤规则
            List<TenantAttendanceRule> allRules = ruleMapper.selectList(new LambdaQueryWrapper<TenantAttendanceRule>()
                    .eq(TenantAttendanceRule::getStatus, 1));
            Map<String, TenantAttendanceRule> ruleMap = allRules.stream()
                    .collect(Collectors.toMap(TenantAttendanceRule::getTenantCode, r -> r));

            // 3. 获取昨日所有已通过的请假单
            List<UserLeave> allLeaves = leaveMapper.selectList(new LambdaQueryWrapper<UserLeave>()
                    .eq(UserLeave::getStatus, LeaveStatusEnum.APPROVED.getCode())
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

            log.info("昨日所有租户考勤核算完成。");

        } catch (Exception ex) {
            log.error("昨日考勤核算任务执行失败", ex);

        } finally {
            // 【关键】：任务执行完，务必清除标记，防止线程池复用污染其他业务
            releaseLock(ATTENDANCE_STAT_LOCK_KEY, attendanceLockValue);
        }
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


    /**
     * 每天凌晨 01:00 执行：Redis 统计数据 → 持久化到 DB
     */
    @Scheduled(cron = "${scheduler.inventory.daily-cron:0 5 0 * * ?}")
    public void inventoryDailyStatTask() {
        String inventoryLockValue = tryRunWithLock(INVENTORY_STAT_LOCK_KEY, 20 * 60);
        if (inventoryLockValue == null) {
            log.info("库存日统计任务已在其他节点执行，本次跳过");
            return;
        }

        log.info("===== 【多租户】库存日统计定时任务开始 =====");

        try {
            LocalDate statDate = LocalDate.now().minusDays(1);
            log.info("统计日期：{}", statDate);

            List<String> tenantCodes = tenantMapper.selectList(new LambdaQueryWrapper<Tenant>()
                            .eq(Tenant::getDeleted, 0)
                            .eq(Tenant::getStatus, 1)
                            .select(Tenant::getTenantCode))
                    .stream()
                    .map(Tenant::getTenantCode)
                    .toList();

            if (tenantCodes.isEmpty()) {
                log.info("无可用租户，任务结束");
                return;
            }

            List<InventoryTrendStatics> insertList = new ArrayList<>();
            List<InventoryTrendStatics> updateList = new ArrayList<>();
            LocalDateTime statDateTime = statDate.atStartOfDay();

            for (String tenantCode : tenantCodes) {
                try {
                    Float totalIn = getTrendMeters(REDIS_TODAY_IN, tenantCode, statDate);
                    Float totalOut = getTrendMeters(REDIS_TODAY_OUT, tenantCode, statDate);

                    InventoryTrendStatics stat = new InventoryTrendStatics();
                    stat.setStatDate(statDateTime);
                    stat.setTenantCode(tenantCode);
                    stat.setDayInMeters(totalIn);
                    stat.setDayOutMeters(totalOut);
                    stat.setUpdateTime(LocalDateTime.now());

                    LambdaQueryWrapper<InventoryTrendStatics> queryWrapper = new LambdaQueryWrapper<>();
                    queryWrapper.eq(InventoryTrendStatics::getStatDate, statDateTime);
                    queryWrapper.eq(InventoryTrendStatics::getTenantCode, tenantCode);
                    InventoryTrendStatics exist = staticsMapper.selectOne(queryWrapper);

                    if (exist != null) {
                        stat.setId(exist.getId());
                        updateList.add(stat);
                    } else {
                        stat.setCreateTime(LocalDateTime.now());
                        insertList.add(stat);
                    }

                    log.info("租户 [{}] 统计完成 → 入库：{}，出库：{}", tenantCode, totalIn, totalOut);
                } catch (Exception e) {
                    log.error("租户 [{}] 统计异常，已跳过", tenantCode, e);
                }
            }

            if (!insertList.isEmpty()) {
                staticsService.saveBatch(insertList);
            }
            if (!updateList.isEmpty()) {
                staticsService.updateBatchById(updateList);
            }

            log.info("===== 【多租户】库存日统计任务全部完成 =====");
        } finally {
            releaseLock(INVENTORY_STAT_LOCK_KEY, inventoryLockValue);
        }
    }

    private Float getTrendMeters(String keyPrefix, String tenantCode, LocalDate statDate) {
        try {
            String value = stringRedisTemplate.opsForValue().get(keyPrefix + tenantCode + ":" + statDate);
            return value == null ? 0f : Float.parseFloat(value);
        } catch (Exception e) {
            log.error("读取库存日统计缓存失败，tenantCode: {}, statDate: {}", tenantCode, statDate, e);
            return 0f;
        }
    }

    private String tryRunWithLock(String lockKey, long expireSeconds) {
        String lockValue = UUID.randomUUID().toString();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, expireSeconds, java.util.concurrent.TimeUnit.SECONDS);
        if (Boolean.TRUE.equals(success)) {
            return lockValue;
        }
        return null;
    }

    private void releaseLock(String lockKey, String lockValue) {
        try {
            String currentValue = stringRedisTemplate.opsForValue().get(lockKey);
            if (lockValue != null && lockValue.equals(currentValue)) {
                stringRedisTemplate.delete(lockKey);
            }
        } catch (Exception e) {
            log.warn("释放定时任务锁失败，lockKey: {}", lockKey, e);
        }
    }
}

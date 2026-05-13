package my.hive_back.module.leave.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive_back.common.utils.CodeGeneratorUtil;
import my.hive_back.module.attendance.PunchStatusEnum;
import my.hive_back.module.attendance.mapper.AttendanceRecordMapper;
import my.hive_back.module.attendance.model.entity.AttendanceRecord;
import my.hive_back.module.leave.ApprovalActionEnum;
import my.hive_back.module.leave.LeaveStatusEnum;
import my.hive_back.module.leave.mapper.LeaveMapper;
import my.hive_back.module.leave.model.dto.AuditRequest;
import my.hive_back.module.leave.model.dto.LeaveSubmitRequest;
import my.hive_back.module.leave.model.entity.UserLeave;
import my.hive_back.module.leave.model.vo.LeaveApprovalListVO;
import my.hive_back.module.tenant.mapper.TenantAttendanceRuleMapper;
import my.hive_back.module.tenant.model.entity.TenantAttendanceRule;
import my.hive_back.module.user.model.entity.User;
import my.hive_back.module.user.service.UserService;
import my.hive_back.module.wechat.service.WechatSubscribeNotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
/**
 * LeaveService 属于小程序后端请假模块，实现核心业务编排与规则逻辑。
 */
@Service
public class LeaveService {

    @Resource
    private LeaveMapper leaveMapper;

    @Resource
    private UserService userService;

    @Resource
    private CodeGeneratorUtil codeGeneratorUtil;

    @Resource
    private AttendanceRecordMapper attendanceRecordMapper;

    @Resource
    private TenantAttendanceRuleMapper tenantAttendanceRuleMapper;

    @Resource
    private WechatSubscribeNotificationService wechatSubscribeNotificationService;

    public boolean isInApprovalLeave(LocalDateTime punchStartTime, LocalDateTime punchEndTime) {
        Long userId = TenantPermissionContext.getUserId();
        String tenantCode = TenantPermissionContext.getTenantCode();
        if (userId == null || tenantCode == null || punchStartTime == null || punchEndTime == null
                || !punchStartTime.isBefore(punchEndTime)) {
            return false;
        }

        QueryWrapper<UserLeave> queryWrapper = new QueryWrapper<UserLeave>()
                .eq("tenant_code", tenantCode)
                .eq("apply_user_id", userId)
                .eq("status", LeaveStatusEnum.APPROVED.getCode())
                .le("start_time", punchStartTime)
                .ge("end_time", punchEndTime);

        Long count = leaveMapper.selectCount(queryWrapper);
        return count > 0;
    }

    @Transactional(rollbackFor = Exception.class)
    public String submitLeaveApproval(LeaveSubmitRequest request) {
        Long userId = TenantPermissionContext.getUserId();
        String tenantCode = TenantPermissionContext.getTenantCode();

        if (request.getStartTime().isAfter(request.getEndTime()) || request.getStartTime().isEqual(request.getEndTime())) {
            throw new BusinessException("提交失败：请假开始时间必须早于结束时间");
        }

        if (request.getStartTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("提交失败：不能提交过去的请假申请(如需补单请联系HR)");
        }

        boolean hasOverlap = leaveMapper.exists(
                new LambdaQueryWrapper<UserLeave>()
                        .eq(UserLeave::getTenantCode, tenantCode)
                        .eq(UserLeave::getApplyUserId, userId)
                        .ne(UserLeave::getStatus, LeaveStatusEnum.REJECTED.getCode())
                        .and(wrapper -> wrapper
                                .lt(UserLeave::getStartTime, request.getEndTime())
                                .gt(UserLeave::getEndTime, request.getStartTime())
                        )
        );
        if (hasOverlap) {
            throw new BusinessException("提交失败：您在该时间段内已有待审批或已通过的请假单，请勿重复提交");
        }

        String leaveCode = codeGeneratorUtil.generateLeaveCode();

        UserLeave approval = new UserLeave();
        approval.setTenantCode(tenantCode);
        approval.setLeaveCode(leaveCode);
        approval.setApplyUserId(userId);
        approval.setLeaveType(request.getLeaveType());
        approval.setStartTime(request.getStartTime());
        approval.setEndTime(request.getEndTime());
        approval.setReason(request.getReason());
        approval.setStatus(LeaveStatusEnum.PENDING.getCode());
        leaveMapper.insert(approval);

        triggerApprovalFlow(approval);
        notifyLeavePendingApprover(approval);
        return leaveCode;
    }

    private void triggerApprovalFlow(UserLeave approval) {
        Long directManagerId = userService.getManagerId(approval.getApplyUserId());
        if (directManagerId == null) {
            throw new BusinessException("提交失败：未找到您的直属审批人，请联系系统管理员配置组织架构");
        }
        approval.setAuditorId(directManagerId);
        leaveMapper.updateById(approval);
    }

    public UserLeave getLeaveByCode(@NotBlank String leaveCode) {
        String tenantCode = TenantPermissionContext.getTenantCode();
        UserLeave userLeave = leaveMapper.selectOne(new LambdaQueryWrapper<UserLeave>()
                .eq(tenantCode != null, UserLeave::getTenantCode, tenantCode)
                .eq(UserLeave::getLeaveCode, leaveCode));
        if (userLeave == null) {
            throw new BusinessException("请假单不存在");
        }
        return userLeave;
    }

    public List<LeaveApprovalListVO> listApprovals(String scope, Integer status) {
        Long userId = TenantPermissionContext.getUserId();
        String tenantCode = TenantPermissionContext.getTenantCode();
        if (tenantCode == null || tenantCode.isBlank()) {
            return List.of();
        }
        LambdaQueryWrapper<UserLeave> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserLeave::getTenantCode, tenantCode);
        if (status != null) {
            queryWrapper.eq(UserLeave::getStatus, status);
        }
        if ("mine".equalsIgnoreCase(scope)) {
            queryWrapper.eq(UserLeave::getApplyUserId, userId);
        } else if ("self_pending".equalsIgnoreCase(scope)) {
            queryWrapper.eq(UserLeave::getApplyUserId, userId)
                    .eq(UserLeave::getAuditorId, userId)
                    .eq(UserLeave::getStatus, LeaveStatusEnum.PENDING.getCode());
        } else if ("others_pending".equalsIgnoreCase(scope)) {
            queryWrapper.eq(UserLeave::getAuditorId, userId)
                    .ne(UserLeave::getApplyUserId, userId)
                    .eq(UserLeave::getStatus, LeaveStatusEnum.PENDING.getCode());
        } else if (!"all".equalsIgnoreCase(scope)) {
            queryWrapper.eq(UserLeave::getAuditorId, userId);
        }
        queryWrapper.orderByDesc(UserLeave::getCreateTime);
        return leaveMapper.selectList(queryWrapper).stream().map(this::toListVO).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void auditLeaveApproval(AuditRequest auditRequest) {
        Long currentUserId = TenantPermissionContext.getUserId();
        UserLeave approval = getLeaveByCode(auditRequest.getLeaveCode());
        if (currentUserId == null || !currentUserId.equals(approval.getAuditorId())) {
            throw new BusinessException("您不是请假单的审批人，不能审批");
        }
        if (approval.getStatus() == null || approval.getStatus() != LeaveStatusEnum.PENDING.getCode()) {
            throw new BusinessException("该请假单已处理，请勿重复审批");
        }

        Long previousAuditorId = approval.getAuditorId();
        if (auditRequest.getAction() == ApprovalActionEnum.APPROVE.getCode()) {
            approval.setAuditComment(auditRequest.getComment());
            long hours = Duration.between(approval.getStartTime(), approval.getEndTime()).toHours();
            double leaveDays = hours / 24.0;
            boolean isFinalApprover = checkIsFinalApprover(currentUserId, leaveDays);

            if (isFinalApprover) {
                approval.setStatus(LeaveStatusEnum.APPROVED.getCode());
                syncLeaveToAttendance(approval);
            } else {
                Long nextManagerId = userService.getManagerId(currentUserId);
                if (nextManagerId != null) {
                    approval.setAuditorId(nextManagerId);
                } else {
                    approval.setStatus(LeaveStatusEnum.APPROVED.getCode());
                    syncLeaveToAttendance(approval);
                }
            }
        } else {
            approval.setStatus(LeaveStatusEnum.REJECTED.getCode());
            approval.setAuditComment(auditRequest.getComment());
        }
        leaveMapper.updateById(approval);
        notifyLeaveAuditChange(approval, previousAuditorId);
    }

    private void notifyLeavePendingApprover(UserLeave approval) {
        wechatSubscribeNotificationService.sendTodoAfterCommit(
                approval.getAuditorId(),
                "请假审批待处理",
                buildApplicantName(approval.getApplyUserId()) + " 提交了请假单 " + approval.getLeaveCode(),
                "/pages/approval/approval"
        );
    }

    private void notifyLeaveAuditChange(UserLeave approval, Long previousAuditorId) {
        if (approval.getStatus() != null && approval.getStatus() == LeaveStatusEnum.PENDING.getCode()
                && approval.getAuditorId() != null && !approval.getAuditorId().equals(previousAuditorId)) {
            notifyLeavePendingApprover(approval);
            return;
        }
        if (approval.getStatus() != null
                && (approval.getStatus() == LeaveStatusEnum.APPROVED.getCode()
                || approval.getStatus() == LeaveStatusEnum.REJECTED.getCode())) {
            wechatSubscribeNotificationService.sendTodoAfterCommit(
                    approval.getApplyUserId(),
                    "请假审批结果",
                    "请假单 " + approval.getLeaveCode() + " " + statusText(approval.getStatus()),
                    "/pages/approval/approval"
            );
        }
    }

    private String buildApplicantName(Long userId) {
        User user = userService.getUserById(userId);
        if (user == null || user.getName() == null || user.getName().isBlank()) {
            return "员工";
        }
        return user.getName();
    }

    private boolean checkIsFinalApprover(Long currentApproverId, double leaveDays) {
        Integer currentRoleLevel = userService.getRoleLevel(currentApproverId);
        if (currentRoleLevel == null) {
            currentRoleLevel = 1;
        }
        if (currentRoleLevel >= 3) {
            return true;
        }
        if (leaveDays <= 2.0) {
            return currentRoleLevel >= 1;
        } else if (leaveDays <= 5.0) {
            return currentRoleLevel >= 2;
        } else {
            return false;
        }
    }

    private void syncLeaveToAttendance(UserLeave approval) {
        LocalDate startDate = approval.getStartTime().toLocalDate();
        LocalDate endDate = approval.getEndTime().toLocalDate();
        Long applyUserId = approval.getApplyUserId();
        String tenantCode = approval.getTenantCode();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        TenantAttendanceRule rule = tenantAttendanceRuleMapper.selectOne(new LambdaQueryWrapper<TenantAttendanceRule>()
                .eq(TenantAttendanceRule::getTenantCode, tenantCode)
                .last("LIMIT 1"));

        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            boolean coverSignIn = isLeaveOverlapTimeRange(currentDate, rule == null ? null : rule.getWorkStartTime(),
                    rule == null ? null : rule.getWorkEndTime(), approval);
            boolean coverSignOut = isLeaveOverlapTimeRange(currentDate, rule == null ? null : rule.getOffWorkStartTime(),
                    rule == null ? null : rule.getOffWorkEndTime(), approval);
            if (!coverSignIn && !coverSignOut) {
                currentDate = currentDate.plusDays(1);
                continue;
            }

            String dateStr = currentDate.format(formatter);
            String punchId = dateStr + "_" + applyUserId;

            AttendanceRecord existingRecord = attendanceRecordMapper.selectOne(
                    new LambdaQueryWrapper<AttendanceRecord>().eq(AttendanceRecord::getPunchId, punchId)
            );

            if (existingRecord == null) {
                AttendanceRecord leaveRecord = new AttendanceRecord();
                leaveRecord.setPunchId(punchId);
                leaveRecord.setUserId(applyUserId);
                leaveRecord.setTenantCode(tenantCode);
                if (coverSignIn) {
                    leaveRecord.setSignInStatus(PunchStatusEnum.LEAVE.getCode());
                }
                if (coverSignOut) {
                    leaveRecord.setSignOutStatus(PunchStatusEnum.LEAVE.getCode());
                }
                attendanceRecordMapper.insert(leaveRecord);
            } else {
                boolean needUpdate = false;
                if (coverSignIn && (existingRecord.getSignInStatus() == null
                        || existingRecord.getSignInStatus().equals(PunchStatusEnum.ABSENT.getCode())
                        || existingRecord.getSignInStatus().equals(PunchStatusEnum.LATE.getCode())
                        || existingRecord.getSignInStatus().equals(PunchStatusEnum.MISS.getCode()))) {
                    existingRecord.setSignInStatus(PunchStatusEnum.LEAVE.getCode());
                    needUpdate = true;
                }
                if (coverSignOut && (existingRecord.getSignOutStatus() == null
                        || existingRecord.getSignOutStatus().equals(PunchStatusEnum.ABSENT.getCode())
                        || existingRecord.getSignOutStatus().equals(PunchStatusEnum.EARLY.getCode())
                        || existingRecord.getSignOutStatus().equals(PunchStatusEnum.MISS.getCode()))) {
                    existingRecord.setSignOutStatus(PunchStatusEnum.LEAVE.getCode());
                    needUpdate = true;
                }
                if (needUpdate) {
                    attendanceRecordMapper.updateById(existingRecord);
                }
            }
            currentDate = currentDate.plusDays(1);
        }
    }

    /**
     * 判断请假时间是否覆盖指定打卡时间段。
     * 如果老租户暂未配置考勤规则，则沿用历史策略按全天请假同步。
     */
    private boolean isLeaveOverlapTimeRange(LocalDate date, LocalTime rangeStart, LocalTime rangeEnd, UserLeave leave) {
        if (rangeStart == null || rangeEnd == null) {
            return true;
        }
        LocalDateTime segmentStart = LocalDateTime.of(date, rangeStart);
        LocalDateTime segmentEnd = LocalDateTime.of(date, rangeEnd);
        return leave.getStartTime().isBefore(segmentEnd) && leave.getEndTime().isAfter(segmentStart);
    }

    private LeaveApprovalListVO toListVO(UserLeave approval) {
        LeaveApprovalListVO vo = new LeaveApprovalListVO();
        vo.setId(approval.getId());
        vo.setLeaveCode(approval.getLeaveCode());
        vo.setLeaveType(approval.getLeaveType());
        vo.setLeaveTypeText(leaveTypeText(approval.getLeaveType()));
        vo.setStartTime(approval.getStartTime());
        vo.setEndTime(approval.getEndTime());
        vo.setReason(approval.getReason());
        vo.setStatus(approval.getStatus());
        vo.setStatusText(statusText(approval.getStatus()));
        vo.setAuditComment(approval.getAuditComment());
        vo.setApplyUserId(approval.getApplyUserId());
        vo.setAuditorId(approval.getAuditorId());
        vo.setCreateTime(approval.getCreateTime());

        User applyUser = userService.getUserById(approval.getApplyUserId());
        if (applyUser != null) {
            vo.setApplyUserName(applyUser.getName());
            vo.setApplyDepartmentName(applyUser.getDepartmentName());
        }
        if (approval.getAuditorId() != null) {
            User auditor = userService.getUserById(approval.getAuditorId());
            if (auditor != null) {
                vo.setAuditorName(auditor.getName());
            }
        }
        return vo;
    }

    private String leaveTypeText(Integer leaveType) {
        if (leaveType == null) return "未填写";
        return switch (leaveType) {
            case 1 -> "事假";
            case 2 -> "病假";
            case 3 -> "年假";
            case 4 -> "调休";
            default -> "其他";
        };
    }

    private String statusText(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 1 -> "待审批";
            case 2 -> "已通过";
            case 3 -> "已拒绝";
            default -> "未知";
        };
    }
}

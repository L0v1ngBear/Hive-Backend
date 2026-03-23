package my.hive_back.module.leave.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import my.hive_back.common.context.TenantPermissionContext;
import my.hive_back.common.exception.BusinessException;
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
import my.hive_back.module.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

    /**
     * 校验员工在打卡时间是否处于「已通过」的请假时段内
     *
     * @param punchStartTime 打卡开始时间
     * @param punchEndTime   打卡结束时间
     * @return true-处于请假中，false-未请假/请假未通过/请假时段不匹配
     */
    public boolean isInApprovalLeave(LocalDateTime punchStartTime, LocalDateTime punchEndTime) {

        Long userId = TenantPermissionContext.getUserId();

        String tenantCode = TenantPermissionContext.getTenantCode();


        // TODO 完善检查请假
        // 构造查询条件：已通过的请假单 + 时间包含打卡时间
        QueryWrapper<UserLeave> queryWrapper = new QueryWrapper<UserLeave>()
                .eq("user_id", userId)
                .eq("tenant_code", tenantCode)
                .eq("leave_status", 0) // 仅校验已通过的请假
                .le("start_time", punchStartTime) // 请假开始时间 ≤ 打卡时间
                .ge("end_time", punchEndTime);  // 请假结束时间 ≥ 打卡时间

        // 查询是否存在符合条件的请假单
        Long count = leaveMapper.selectCount(queryWrapper);
        return count > 0;
    }


    /**
     * 扩展：校验员工当日是否有全天请假（简化版，按日期匹配）
     * @param punchDate 打卡日期
     * @return true-当日全天请假
     */
//    public boolean isWholeDayLeave(LocalDate punchDate) {
//        // 构造当日时间范围：00:00:00 ~ 23:59:59
//        LocalDateTime startOfDay = punchDate.atStartOfDay();
//        LocalDateTime endOfDay = punchDate.atTime(23, 59, 59);
//
//        QueryWrapper<LeaveApplication> queryWrapper = new QueryWrapper<LeaveApplication>()
//                .eq("user_id", userId)
//                .eq("company_id", companyId)
//                .eq("leave_status", LeaveApplication.LeaveStatusEnum.APPROVED.getCode())
//                .le("start_time", startOfDay) // 请假开始 ≤ 当日0点
//                .ge("end_time", endOfDay);    // 请假结束 ≥ 当日23:59
//
//        return leaveApplicationMapper.selectCount(queryWrapper) > 0;
//    }

    /**
     * 提交请假申请
     */
    @Transactional(rollbackFor = Exception.class)
    public String submitLeaveApproval(LeaveSubmitRequest request) {

        Long userId = TenantPermissionContext.getUserId();
        String tenantCode = TenantPermissionContext.getTenantCode();

        // 1. 基础业务校验
        if (request.getStartTime().isAfter(request.getEndTime()) || request.getStartTime().isEqual(request.getEndTime())) {
            throw new BusinessException("提交失败：请假开始时间必须早于结束时间");
        }

        if (request.getStartTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("提交失败：不能提交过去的请假申请(如需补单请联系HR)");
        }

        // 2. 防重叠校验 (关键！防止员工同一个时间段请假两次)
        boolean hasOverlap = leaveMapper.exists(
                new LambdaQueryWrapper<UserLeave>()
                        .eq(UserLeave::getApplyUserId, userId)
                        .eq(UserLeave::getTenantCode, tenantCode)
                        .ne(UserLeave::getStatus, 2) // 排除已拒绝的单子
                        .and(wrapper -> wrapper
                                // 重叠逻辑：现有的开始时间 < 申请的结束时间 且 现有的结束时间 > 申请的开始时间
                                .lt(UserLeave::getStartTime, request.getEndTime())
                                .gt(UserLeave::getEndTime, request.getStartTime())
                        )
        );
        if (hasOverlap) {
            throw new BusinessException("提交失败：您在该时间段内已有待审批或已通过的请假单，请勿重复提交");
        }
        // 获取请假单编码
        String leaveCode = codeGeneratorUtil.generateLeaveCode();

        // 3. 构建并保存请假单实体
        UserLeave approval = new UserLeave();
        approval.setTenantCode(tenantCode);
        approval.setLeaveCode(leaveCode);
        approval.setApplyUserId(userId);
        approval.setLeaveType(request.getLeaveType());
        approval.setStartTime(request.getStartTime());
        approval.setEndTime(request.getEndTime());
        approval.setReason(request.getReason());

        // 初始化状态：0-待审批
        approval.setStatus(LeaveStatusEnum.PENDING.getCode());

        // TODO: 可选，如果请假涉及扣减年假额度，需要在这里检查该员工剩余年假是否充足

        // 必须先 insert 生成单据 ID，然后再分配审批人
        leaveMapper.insert(approval);

        // 4. 触发审批流 (寻找第一节点审批人)
        triggerApprovalFlow(approval);

        return leaveCode;
    }

    /**
     * 触发轻量级审批流
     */
    private void triggerApprovalFlow(UserLeave approval) {

        // 1. 查出该员工的直属主管 ID
        Long directManagerId = userService.getManagerId(approval.getApplyUserId());

        if (directManagerId == null) {

            // （严格模式）：不允许提交
            throw new BusinessException("提交失败：未找到您的直属审批人，请联系系统管理员配置组织架构");

        } else {
            // 2. 正常流转：将当前单据的处理人（接力棒）交给直属主管
            approval.setAuditorId(directManagerId);
            leaveMapper.updateById(approval);

            // 3. TODO 发送站内信、钉钉或邮件通知主管
            // messageService.sendNotification(directManagerId, "您有一条新的员工请假申请待审批。");
        }
    }

    public UserLeave getLeaveByCode(@NotBlank String leaveCode) {
        LambdaQueryWrapper<UserLeave> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserLeave::getLeaveCode, leaveCode);
        UserLeave userLeave = leaveMapper.selectOne(queryWrapper);
        if (userLeave == null) {
            throw new BusinessException("请假单不存在");
        }
        return userLeave;
    }

    @Transactional(rollbackFor = Exception.class)
    public void auditLeaveApproval(AuditRequest auditRequest) {
        Long currentUserId = TenantPermissionContext.getUserId();

        UserLeave approval = getLeaveByCode(auditRequest.getLeaveCode());
        if (!approval.getAuditorId().equals(currentUserId)) {
            throw new BusinessException("您不是请假单的审批人，不能审批");
        }

        if (auditRequest.getAction().equals(ApprovalActionEnum.APPROVE.getCode())) {
            approval.setAuditComment(auditRequest.getComment());

            // 1. 计算请假天数 (简化版：按24小时=1天算。实际业务可能需要排除非工作时间)
            long hours = Duration.between(approval.getStartTime(), approval.getEndTime()).toHours();
            double leaveDays = hours / 24.0;

            // 2. 判断当前人是不是“最终审批人”
            boolean isFinalApprover = checkIsFinalApprover(currentUserId, leaveDays);

            if (isFinalApprover) {
                // [终审节点]：权限足够，流程结束
                approval.setStatus(LeaveStatusEnum.APPROVED.getCode());
            } else {
                // [中间节点]：权限不够，需要流转给更上一级领导
                Long nextManagerId = null;

                 nextManagerId = userService.getManagerId(currentUserId);

                if (nextManagerId != null) {
                    // 交接棒：更新当前处理人为下一级领导，状态依然是 PENDING(待审批)
                    approval.setAuditorId(nextManagerId);
                    // TODO: 通知下一级领导来审批
                } else {
                    // 兜底：找不到更上级了（比如当前已经是最大老板），强制作为终审结束
                    approval.setStatus(LeaveStatusEnum.APPROVED.getCode());
                    syncLeaveToAttendance(approval);
                }
            }
        } else {
            approval.setStatus(LeaveStatusEnum.REJECTED.getCode());
            approval.setAuditComment(auditRequest.getComment());
            // TODO: (可选) 发送通知给申请人：“您的请假被拒绝”
        }
        leaveMapper.updateById(approval);
    }

    /**
     * 判断当前审批人是否是最后一道关卡 (轻量级规则引擎)
     */
    private boolean checkIsFinalApprover(Long currentApproverId, double leaveDays) {
        // TODO: 从你的 userService 获取该员工的职级 (Level: 1-主管, 2-总监, 3-老板)

        Integer currentRoleLevel = userService.getRoleLevel(currentApproverId);

        if (currentRoleLevel >= 3) {
            return true; // 老板批的，直接结束
        }

        // 规则示例：<=2天主管可终审；3~5天需总监终审；>5天需老板终审
        if (leaveDays <= 2.0) {
            return currentRoleLevel >= 1;
        } else if (leaveDays <= 5.0) {
            return currentRoleLevel >= 2;
        } else {
            return false;
        }
    }

    /**
     * 【核心联动】：将请假记录同步到考勤表，抵消缺勤/早退异常
     */
    private void syncLeaveToAttendance(UserLeave approval) {
        LocalDate startDate = approval.getStartTime().toLocalDate();
        LocalDate endDate = approval.getEndTime().toLocalDate();
        Long applyUserId = approval.getApplyUserId();
        String tenantCode = approval.getTenantCode();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

        // 遍历请假期间的每一天
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            String dateStr = currentDate.format(formatter);
            String punchId = dateStr + "_" + applyUserId;

            // 查询当天是否已有打卡记录
            AttendanceRecord existingRecord = attendanceRecordMapper.selectOne(
                    new LambdaQueryWrapper<AttendanceRecord>().eq(AttendanceRecord::getPunchId, punchId)
            );

            if (existingRecord == null) {
                // 还没有考勤记录 -> 预先插入一条纯“请假”状态的空记录
                AttendanceRecord leaveRecord = new AttendanceRecord();
                leaveRecord.setPunchId(punchId);
                leaveRecord.setUserId(applyUserId);
                leaveRecord.setTenantCode(tenantCode);

                // 将上下班状态都标记为“请假”
                leaveRecord.setSignInStatus(PunchStatusEnum.LEAVE.getCode());
                leaveRecord.setSignOutStatus(PunchStatusEnum.LEAVE.getCode());

                attendanceRecordMapper.insert(leaveRecord);
            } else {
                // 已有打卡记录 (比如下午请假，早上已经打过卡了) -> 修复异常状态为“请假”
                boolean needUpdate = false;

                // 如果上班没打卡被记成了缺勤，改为请假
                if (existingRecord.getSignInStatus() == null) {
                    existingRecord.setSignInStatus(PunchStatusEnum.LEAVE.getCode());
                    needUpdate = true;
                }
                // 如果下班没打卡被记成了缺卡/早退/缺勤，改为请假
                if (existingRecord.getSignOutStatus() == null
                        || existingRecord.getSignOutStatus().equals(PunchStatusEnum.ABSENT.getCode())
                        || existingRecord.getSignOutStatus().equals(PunchStatusEnum.EARLY.getCode())
                        || existingRecord.getSignOutStatus().equals(PunchStatusEnum.MISS.getCode())) {
                    existingRecord.setSignOutStatus(PunchStatusEnum.LEAVE.getCode());
                    needUpdate = true;
                }

                if (needUpdate) {
                    attendanceRecordMapper.updateById(existingRecord);
                }
            }
            // 天数 + 1，继续处理下一天
            currentDate = currentDate.plusDays(1);
        }
    }
}
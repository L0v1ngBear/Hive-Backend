package my.hive_back.module.leave.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.Resource;
import my.hive_back.common.context.TenantPermissionContext;
import my.hive_back.module.leave.mapper.LeaveMapper;
import my.hive_back.module.leave.model.entity.UserLeave;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class LeaveService {

    @Resource
    private LeaveMapper leaveMapper;

    /**
     * 校验员工在打卡时间是否处于「已通过」的请假时段内
     * @param punchStartTime 打卡开始时间
     * @param punchEndTime 打卡结束时间
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
}

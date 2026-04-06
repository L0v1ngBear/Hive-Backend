package my.hive_back.api.approval;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import my.hive_back.common.dto.Result;
import my.hive_back.module.leave.model.dto.AuditRequest;
import my.hive_back.module.leave.model.dto.LeaveSubmitRequest;
import my.hive_back.module.leave.model.entity.UserLeave;
import my.hive_back.module.leave.model.vo.LeaveDetailVO;
import my.hive_back.module.leave.service.LeaveService;
import my.hive_back.module.user.model.entity.User;
import my.hive_back.module.user.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/approval")
@Validated
public class ApprovalController {

    // 假设你注入了对应的 Service
    @Resource
    private LeaveService leaveService;

    @Resource
    private UserService userService;

    // @Resource
    // private FinanceApprovalService financeApprovalService;

    // ==========================================
    //               一、 请假审批模块
    // ==========================================

    /**
     * 1. 发起请假申请
     * @param request 包含请假类型、开始时间、结束时间、事由等
     */
    @PostMapping("/leave/submit")
    public Result<String> submitLeaveApproval(@Valid @RequestBody LeaveSubmitRequest request) {
        String leaveCode = leaveService.submitLeaveApproval(request);
        return Result.success(leaveCode);
    }

    /**
     * 2. 查询请假审批详情
     * @param leaveCode 请假单 ID
     */
    @GetMapping("/leave/{leaveCode}")
    public Result<LeaveDetailVO> getLeaveApprovalDetail(@NotBlank @PathVariable("leaveCode") String leaveCode) {
        // TODO: 查询请假单详情及审批进度
        UserLeave userLeave = leaveService.getLeaveByCode(leaveCode);
        LeaveDetailVO leaveDetailVO = new LeaveDetailVO();
        User user = userService.getUserById(userLeave.getApplyUserId());
        User manager = userService.getUserById(userLeave.getAuditorId());
        BeanUtils.copyProperties(userLeave, leaveDetailVO);
        leaveDetailVO.setApplyUserName(user.getName());
        leaveDetailVO.setAuditorName(manager.getName());
        return Result.success(leaveDetailVO);
    }

    /**
     * 3. 处理请假审批 (同意/拒绝)
     * @param auditRequest 包含审批单ID、审批动作(同意/拒绝)、审批意见等
     */
    @PostMapping("/leave/audit")
    public Result<?> auditLeaveApproval(@RequestBody AuditRequest auditRequest) {
        leaveService.auditLeaveApproval(auditRequest);
        return Result.success("请假审批处理完成");
    }


    // ==========================================
    //               二、 财务审批模块
    // ==========================================

    /**
     * 1. 发起财务报销/审批申请
     * @param request 包含报销明细、金额、发票附件、收款账户等
     */
    @PostMapping("/finance/submit")
    public Object submitFinanceApproval(@RequestBody Object request) { // 建议替换为 FinanceSubmitRequest DTO
        // TODO: 调用 Service 保存财务审批单
        return "财务审批申请提交成功";
    }

    /**
     * 2. 查询财务审批详情
     * @param id 审批单 ID
     */
    @GetMapping("/finance/{id}")
    public Object getFinanceApprovalDetail(@PathVariable("id") Long id) {
        // TODO: 查询财务审批单详情
        return "财务审批单 " + id + " 的详情";
    }

    /**
     * 3. 处理财务审批 (同意/拒绝)
     * @param auditRequest 包含审批单ID、审批动作、打款记录(若是出纳节点)等
     */
    @PostMapping("/finance/audit")
    public Object auditFinanceApproval(@RequestBody Object auditRequest) {
        // TODO: 更新审批流状态。如果是最后一步，可能涉及调用第三方支付接口或更新财务对账表
        return "财务审批处理完成";
    }

}
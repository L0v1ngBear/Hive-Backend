package my.hive_back.api.approval;

import my.hive_back.module.tenant.TenantFeatureEnum;
import my.hive_back.module.sys.model.enums.PermissionCodeEnum;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import my.hive.common.annotation.RequirePermission;
import my.hive.common.dto.Result;
import my.hive_back.common.enums.QueryScopeEnum;
import my.hive_back.common.tenant.RequireTenantFeature;
import my.hive_back.module.finance.model.dto.FinanceAuditRequest;
import my.hive_back.module.finance.model.dto.FinanceSubmitRequest;
import my.hive_back.module.finance.model.vo.FinanceApprovalVO;
import my.hive_back.module.finance.service.FinanceApprovalService;
import my.hive_back.module.leave.model.dto.AuditRequest;
import my.hive_back.module.leave.model.dto.LeaveSubmitRequest;
import my.hive_back.module.leave.model.entity.UserLeave;
import my.hive_back.module.leave.model.vo.LeaveApprovalListVO;
import my.hive_back.module.leave.model.vo.LeaveDetailVO;
import my.hive_back.module.leave.service.LeaveService;
import my.hive_back.module.user.model.entity.User;
import my.hive_back.module.user.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 审批中心控制器，负责处理请假和财务审批相关的接口请求。
 */
@RestController
@RequestMapping("/approval")
@RequireTenantFeature(TenantFeatureEnum.CODE_MODULE_APPROVAL)
@Validated
public class ApprovalController {

    @Resource
    private LeaveService leaveService;

    @Resource
    private UserService userService;

    @Resource
    private FinanceApprovalService financeApprovalService;

    @PostMapping("/leave/submit")
    @RequirePermission(value = PermissionCodeEnum.CODE_APPROVAL_LEAVE_SUBMIT, message = "您没有权限提交请假申请")
    public Result<String> submitLeaveApproval(@Valid @RequestBody LeaveSubmitRequest request) {
        return Result.success(leaveService.submitLeaveApproval(request));
    }

    @GetMapping("/leave/list")
    @RequirePermission(value = PermissionCodeEnum.CODE_APPROVAL_LEAVE, message = "您没有权限查看请假审批列表")
    public Result<List<LeaveApprovalListVO>> listLeaveApprovals(@RequestParam(defaultValue = QueryScopeEnum.CODE_PENDING) String scope,
                                                                @RequestParam(required = false) Integer status) {
        return Result.success(leaveService.listApprovals(scope, status));
    }

    @GetMapping("/leave/{leaveCode}")
    @RequirePermission(value = PermissionCodeEnum.CODE_APPROVAL_LEAVE_DETAIL, message = "您没有权限查看请假详情")
    public Result<LeaveDetailVO> getLeaveApprovalDetail(@NotBlank @PathVariable("leaveCode") String leaveCode) {
        UserLeave userLeave = leaveService.getLeaveByCode(leaveCode);
        LeaveDetailVO leaveDetailVO = new LeaveDetailVO();
        User user = userService.getUserById(userLeave.getApplyUserId());
        User manager = userLeave.getAuditorId() == null ? null : userService.getUserById(userLeave.getAuditorId());
        BeanUtils.copyProperties(userLeave, leaveDetailVO);
        leaveDetailVO.setApplyUserName(user == null ? "未知员工" : user.getName());
        leaveDetailVO.setAuditorName(manager == null ? "待分配" : manager.getName());
        return Result.success(leaveDetailVO);
    }

    @PostMapping("/leave/audit")
    @RequirePermission(value = PermissionCodeEnum.CODE_APPROVAL_LEAVE_AUDIT, message = "您没有权限审批请假单")
    public Result<String> auditLeaveApproval(@Valid @RequestBody AuditRequest auditRequest) {
        leaveService.auditLeaveApproval(auditRequest);
        return Result.success("请假审批处理完成");
    }

    @PostMapping("/finance/submit")
    @RequirePermission(value = PermissionCodeEnum.CODE_APPROVAL_FINANCE_SUBMIT, message = "您没有权限提交财务审批")
    public Result<String> submitFinanceApproval(@Valid @RequestBody FinanceSubmitRequest request) {
        return Result.success(financeApprovalService.submit(request));
    }

    @GetMapping("/finance/list")
    @RequirePermission(value = PermissionCodeEnum.CODE_APPROVAL_FINANCE, message = "您没有权限查看财务审批列表")
    public Result<List<FinanceApprovalVO>> listFinanceApprovals(@RequestParam(defaultValue = QueryScopeEnum.CODE_PENDING) String scope,
                                                                @RequestParam(required = false) Integer status) {
        return Result.success(financeApprovalService.list(scope, status));
    }

    @GetMapping("/finance/{approvalCode}")
    @RequirePermission(value = PermissionCodeEnum.CODE_APPROVAL_FINANCE_DETAIL, message = "您没有权限查看财务审批详情")
    public Result<FinanceApprovalVO> getFinanceApprovalDetail(@PathVariable("approvalCode") String approvalCode) {
        return Result.success(financeApprovalService.detail(approvalCode));
    }

    @PostMapping("/finance/audit")
    @RequirePermission(value = PermissionCodeEnum.CODE_APPROVAL_FINANCE_AUDIT, message = "您没有权限审批财务单")
    public Result<String> auditFinanceApproval(@Valid @RequestBody FinanceAuditRequest auditRequest) {
        financeApprovalService.audit(auditRequest);
        return Result.success("财务审批处理完成");
    }
}

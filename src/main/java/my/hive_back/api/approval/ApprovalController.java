package my.hive_back.api.approval;

import my.hive_back.module.tenant.TenantFeatureEnum;
import my.hive_back.module.sys.model.enums.PermissionCodeEnum;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import my.hive.common.annotation.CollectLog;
import my.hive.common.annotation.RequirePermission;
import my.hive.common.dto.Result;
import my.hive_back.common.enums.QueryScopeEnum;
import my.hive_back.common.storage.BusinessImageAttachmentService;
import my.hive_back.common.storage.BusinessImageAttachmentVO;
import my.hive_back.common.tenant.RequireTenantFeature;
import my.hive_back.module.approval.model.dto.OrderApprovalAuditRequest;
import my.hive_back.module.approval.model.dto.QualityAuditRequest;
import my.hive_back.module.approval.model.vo.ApprovalAuditorOptionVO;
import my.hive_back.module.approval.model.vo.ApprovalSummaryVO;
import my.hive_back.module.approval.model.vo.OrderApprovalVO;
import my.hive_back.module.approval.model.vo.QualityApprovalVO;
import my.hive_back.module.approval.service.ApprovalCenterService;
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
import my.hive_back.module.resignation.model.dto.ResignationAuditRequest;
import my.hive_back.module.resignation.model.dto.ResignationSubmitRequest;
import my.hive_back.module.resignation.model.vo.ResignationApprovalVO;
import my.hive_back.module.resignation.service.ResignationApprovalService;
import my.hive_back.module.user.model.entity.User;
import my.hive_back.module.user.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @Resource
    private ResignationApprovalService resignationApprovalService;

    @Resource
    private ApprovalCenterService approvalCenterService;

    @Resource
    private BusinessImageAttachmentService businessImageAttachmentService;

    @GetMapping("/summary")
    public Result<ApprovalSummaryVO> summary() {
        return Result.success(approvalCenterService.summary());
    }

    @GetMapping("/auditors")
    public Result<List<ApprovalAuditorOptionVO>> listAuditors(@RequestParam String type,
                                                              @RequestParam(required = false) String keyword,
                                                              @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(approvalCenterService.listAuditorOptions(type, keyword, limit));
    }

    @PostMapping("/leave/submit")
    @RequirePermission(value = PermissionCodeEnum.CODE_APPROVAL_LEAVE_SUBMIT, message = "您没有权限提交请假申请")
    @CollectLog(module = "approval", action = "submit_leave", bizType = "leave_approval", description = "小程序提交请假审批")
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
    @CollectLog(module = "approval", action = "audit_leave", bizType = "leave_approval", bizNo = "#auditRequest.leaveCode", description = "小程序审批请假单")
    public Result<String> auditLeaveApproval(@Valid @RequestBody AuditRequest auditRequest) {
        leaveService.auditLeaveApproval(auditRequest);
        return Result.success("请假审批处理完成");
    }

    @PostMapping("/finance/submit")
    @RequirePermission(value = PermissionCodeEnum.CODE_APPROVAL_FINANCE_SUBMIT, message = "您没有权限提交财务审批")
    @CollectLog(module = "approval", action = "submit_finance", bizType = "finance_approval", description = "小程序提交财务审批")
    public Result<String> submitFinanceApproval(@Valid @RequestBody FinanceSubmitRequest request) {
        return Result.success(financeApprovalService.submit(request));
    }

    @PostMapping("/finance/attachment/upload")
    @RequirePermission(value = PermissionCodeEnum.CODE_APPROVAL_FINANCE_SUBMIT, message = "您没有权限上传财务图片")
    @CollectLog(module = "approval", action = "mini_upload_finance_image", bizType = "finance_approval", description = "小程序上传财务审批图片")
    public Result<BusinessImageAttachmentVO> uploadFinanceAttachment(@RequestParam("file") MultipartFile file) {
        return Result.success(businessImageAttachmentService.uploadImage(file, "finance"));
    }

    @GetMapping("/finance/attachment/download")
    @RequirePermission(value = PermissionCodeEnum.CODE_APPROVAL_FINANCE_DETAIL, message = "您没有权限查看财务图片")
    public ResponseEntity<org.springframework.core.io.Resource> downloadFinanceAttachment(@RequestParam("url") String url) {
        org.springframework.core.io.Resource resource = businessImageAttachmentService.load(url, "finance");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(resource);
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
    @CollectLog(module = "approval", action = "audit_finance", bizType = "finance_approval", bizNo = "#auditRequest.approvalCode", description = "小程序审批财务单")
    public Result<String> auditFinanceApproval(@Valid @RequestBody FinanceAuditRequest auditRequest) {
        financeApprovalService.audit(auditRequest);
        return Result.success("财务审批处理完成");
    }

    @PostMapping("/resignation/submit")
    @RequirePermission(value = PermissionCodeEnum.CODE_APPROVAL_RESIGNATION_SUBMIT, message = "您没有权限提交离职审批")
    @CollectLog(module = "approval", action = "submit_resignation", bizType = "resignation_approval", description = "小程序提交离职审批")
    public Result<String> submitResignationApproval(@Valid @RequestBody ResignationSubmitRequest request) {
        return Result.success(resignationApprovalService.submit(request));
    }

    @GetMapping("/resignation/list")
    @RequirePermission(value = PermissionCodeEnum.CODE_APPROVAL_RESIGNATION, message = "您没有权限查看离职审批列表")
    public Result<List<ResignationApprovalVO>> listResignationApprovals(@RequestParam(defaultValue = QueryScopeEnum.CODE_PENDING) String scope,
                                                                        @RequestParam(required = false) Integer status) {
        return Result.success(resignationApprovalService.list(scope, status));
    }

    @GetMapping("/resignation/{resignationCode}")
    @RequirePermission(value = PermissionCodeEnum.CODE_APPROVAL_RESIGNATION_DETAIL, message = "您没有权限查看离职审批详情")
    public Result<ResignationApprovalVO> getResignationApprovalDetail(@PathVariable("resignationCode") String resignationCode) {
        return Result.success(resignationApprovalService.detail(resignationCode));
    }

    @PostMapping("/resignation/audit")
    @RequirePermission(value = PermissionCodeEnum.CODE_APPROVAL_RESIGNATION_AUDIT, message = "您没有权限审批离职单")
    @CollectLog(module = "approval", action = "audit_resignation", bizType = "resignation_approval", bizNo = "#auditRequest.resignationCode", description = "小程序审批离职单")
    public Result<String> auditResignationApproval(@Valid @RequestBody ResignationAuditRequest auditRequest) {
        resignationApprovalService.audit(auditRequest);
        return Result.success("离职审批处理完成");
    }

    @GetMapping("/order/list")
    @RequirePermission(value = PermissionCodeEnum.CODE_ORDER_LIST, message = "您没有权限查看订单审批列表")
    public Result<List<OrderApprovalVO>> listOrderApprovals() {
        return Result.success(approvalCenterService.listOrderApprovals());
    }

    @GetMapping("/order/{orderType}/{orderId}")
    @RequirePermission(value = PermissionCodeEnum.CODE_ORDER_DETAIL, message = "您没有权限查看订单审批详情")
    public Result<OrderApprovalVO> getOrderApprovalDetail(@NotBlank @PathVariable String orderType,
                                                          @NotBlank @PathVariable String orderId) {
        return Result.success(approvalCenterService.detail(orderType, orderId));
    }

    @PostMapping("/order/audit")
    @RequirePermission(value = PermissionCodeEnum.CODE_ORDER_LIST, message = "您没有权限处理订单审批")
    @CollectLog(module = "approval", action = "audit_order", bizType = "order_approval", bizNo = "#auditRequest.orderId", description = "小程序确认待审批订单")
    public Result<String> auditOrderApproval(@Valid @RequestBody OrderApprovalAuditRequest auditRequest) {
        approvalCenterService.audit(auditRequest);
        return Result.success("订单审批处理完成");
    }

    @GetMapping("/quality/list")
    @RequirePermission(value = PermissionCodeEnum.CODE_BADPRODUCT_PROCESS, message = "您没有权限查看质量审核列表")
    public Result<List<QualityApprovalVO>> listQualityApprovals(@RequestParam(required = false) Integer limit) {
        return Result.success(approvalCenterService.listQualityApprovals(limit));
    }

    @GetMapping("/quality/{defectiveId}")
    @RequirePermission(value = PermissionCodeEnum.CODE_BADPRODUCT_PROCESS, message = "您没有权限查看质量审核详情")
    public Result<QualityApprovalVO> getQualityApprovalDetail(@NotBlank @PathVariable("defectiveId") String defectiveId) {
        return Result.success(approvalCenterService.getQualityApprovalDetail(defectiveId));
    }

    @PostMapping("/quality/audit")
    @RequirePermission(value = PermissionCodeEnum.CODE_BADPRODUCT_PROCESS, message = "您没有权限审核质量处理")
    @CollectLog(module = "approval", action = "audit_quality", bizType = "quality_approval", bizNo = "#auditRequest.defectiveId", description = "小程序审核质量处理")
    public Result<String> auditQualityApproval(@Valid @RequestBody QualityAuditRequest auditRequest) {
        approvalCenterService.auditQualityApproval(auditRequest);
        return Result.success("质量审核处理完成");
    }
}

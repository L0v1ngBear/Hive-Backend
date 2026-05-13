package my.hive_back.module.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive_back.common.utils.CodeGeneratorUtil;
import my.hive_back.module.finance.mapper.FinanceApprovalMapper;
import my.hive_back.module.finance.model.dto.FinanceAuditRequest;
import my.hive_back.module.finance.model.dto.FinanceSubmitRequest;
import my.hive_back.module.finance.model.entity.FinanceApproval;
import my.hive_back.module.finance.model.vo.FinanceApprovalVO;
import my.hive_back.module.leave.ApprovalActionEnum;
import my.hive_back.module.user.model.entity.User;
import my.hive_back.module.user.service.UserService;
import my.hive_back.module.wechat.service.WechatSubscribeNotificationService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
/**
 * FinanceApprovalService 属于小程序后端财务模块，实现核心业务编排与规则逻辑。
 */
@Service
public class FinanceApprovalService {

    private static final int STATUS_PENDING = 1;
    private static final int STATUS_APPROVED = 2;
    private static final int STATUS_REJECTED = 3;

    @Resource
    private FinanceApprovalMapper financeApprovalMapper;

    @Resource
    private UserService userService;

    @Resource
    private CodeGeneratorUtil codeGeneratorUtil;

    @Resource
    private WechatSubscribeNotificationService wechatSubscribeNotificationService;

    @Transactional(rollbackFor = Exception.class)
    public String submit(FinanceSubmitRequest request) {
        Long userId = TenantPermissionContext.getUserId();
        String tenantCode = TenantPermissionContext.getTenantCode();
        Long managerId = userService.getManagerId(userId);
        if (managerId == null) {
            throw new BusinessException("未找到直属审批人，请联系管理员配置组织架构");
        }

        FinanceApproval approval = new FinanceApproval();
        approval.setApprovalCode(codeGeneratorUtil.generateFinanceApprovalCode());
        approval.setTenantCode(tenantCode);
        approval.setApplyUserId(userId);
        approval.setCategory(request.getCategory().trim());
        approval.setAmount(request.getAmount());
        approval.setReason(request.getReason().trim());
        approval.setAttachmentUrl(request.getAttachmentUrl());
        approval.setStatus(STATUS_PENDING);
        approval.setAuditorId(managerId);
        financeApprovalMapper.insert(approval);
        notifyFinancePendingApprover(approval);
        return approval.getApprovalCode();
    }

    public FinanceApproval getByCode(String approvalCode) {
        String tenantCode = TenantPermissionContext.getTenantCode();
        FinanceApproval approval = financeApprovalMapper.selectOne(new LambdaQueryWrapper<FinanceApproval>()
                .eq(tenantCode != null, FinanceApproval::getTenantCode, tenantCode)
                .eq(FinanceApproval::getApprovalCode, approvalCode));
        if (approval == null) {
            throw new BusinessException("财务审批单不存在");
        }
        return approval;
    }

    public FinanceApprovalVO detail(String approvalCode) {
        return toVO(getByCode(approvalCode));
    }

    public List<FinanceApprovalVO> list(String scope, Integer status) {
        Long userId = TenantPermissionContext.getUserId();
        String tenantCode = TenantPermissionContext.getTenantCode();
        if (tenantCode == null || tenantCode.isBlank()) {
            return List.of();
        }
        LambdaQueryWrapper<FinanceApproval> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FinanceApproval::getTenantCode, tenantCode);
        if (status != null) {
            queryWrapper.eq(FinanceApproval::getStatus, status);
        }
        if ("mine".equalsIgnoreCase(scope)) {
            queryWrapper.eq(FinanceApproval::getApplyUserId, userId);
        } else if ("self_pending".equalsIgnoreCase(scope)) {
            queryWrapper.eq(FinanceApproval::getApplyUserId, userId)
                    .eq(FinanceApproval::getAuditorId, userId)
                    .eq(FinanceApproval::getStatus, STATUS_PENDING);
        } else if ("others_pending".equalsIgnoreCase(scope)) {
            queryWrapper.eq(FinanceApproval::getAuditorId, userId)
                    .ne(FinanceApproval::getApplyUserId, userId)
                    .eq(FinanceApproval::getStatus, STATUS_PENDING);
        } else if (!"all".equalsIgnoreCase(scope)) {
            queryWrapper.eq(FinanceApproval::getAuditorId, userId);
        }
        queryWrapper.orderByDesc(FinanceApproval::getCreateTime);
        return financeApprovalMapper.selectList(queryWrapper).stream().map(this::toVO).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void audit(FinanceAuditRequest request) {
        Long currentUserId = TenantPermissionContext.getUserId();
        FinanceApproval approval = getByCode(request.getApprovalCode());
        if (currentUserId == null || !currentUserId.equals(approval.getAuditorId())) {
            throw new BusinessException("您不是该财务审批单当前审批人");
        }
        if (approval.getStatus() != STATUS_PENDING) {
            throw new BusinessException("该财务审批单已处理，请勿重复审批");
        }

        Long previousAuditorId = approval.getAuditorId();
        approval.setAuditComment(request.getComment());
        if (ApprovalActionEnum.APPROVE.getCode() == request.getAction()) {
            Long nextManagerId = userService.getManagerId(currentUserId);
            Integer roleLevel = userService.getRoleLevel(currentUserId);
            if (roleLevel != null && roleLevel >= 2 || nextManagerId == null) {
                approval.setStatus(STATUS_APPROVED);
            } else {
                approval.setAuditorId(nextManagerId);
            }
        } else {
            approval.setStatus(STATUS_REJECTED);
        }
        financeApprovalMapper.updateById(approval);
        notifyFinanceAuditChange(approval, previousAuditorId);
    }

    private void notifyFinancePendingApprover(FinanceApproval approval) {
        wechatSubscribeNotificationService.sendTodoAfterCommit(
                approval.getAuditorId(),
                "财务审批待处理",
                buildApplicantName(approval.getApplyUserId()) + " 提交了财务单 " + approval.getApprovalCode(),
                "/pages/approval/approval"
        );
    }

    private void notifyFinanceAuditChange(FinanceApproval approval, Long previousAuditorId) {
        if (approval.getStatus() != null && approval.getStatus() == STATUS_PENDING
                && approval.getAuditorId() != null && !approval.getAuditorId().equals(previousAuditorId)) {
            notifyFinancePendingApprover(approval);
            return;
        }
        if (approval.getStatus() != null && (approval.getStatus() == STATUS_APPROVED || approval.getStatus() == STATUS_REJECTED)) {
            wechatSubscribeNotificationService.sendTodoAfterCommit(
                    approval.getApplyUserId(),
                    "财务审批结果",
                    "财务单 " + approval.getApprovalCode() + " " + statusText(approval.getStatus()),
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

    private FinanceApprovalVO toVO(FinanceApproval approval) {
        FinanceApprovalVO vo = new FinanceApprovalVO();
        BeanUtils.copyProperties(approval, vo);

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
        vo.setStatusText(statusText(approval.getStatus()));
        return vo;
    }

    private String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case STATUS_PENDING -> "待审批";
            case STATUS_APPROVED -> "已通过";
            case STATUS_REJECTED -> "已拒绝";
            default -> "未知";
        };
    }
}

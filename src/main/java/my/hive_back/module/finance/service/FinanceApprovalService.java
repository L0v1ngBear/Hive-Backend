package my.hive_back.module.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive_back.common.security.InternalUploadUrlValidator;
import my.hive_back.common.utils.CodeGeneratorUtil;
import my.hive_back.module.finance.mapper.FinanceApprovalMapper;
import my.hive_back.module.finance.model.dto.FinanceAuditRequest;
import my.hive_back.module.finance.model.dto.FinanceSubmitRequest;
import my.hive_back.module.finance.model.entity.FinanceApproval;
import my.hive_back.module.finance.model.vo.FinanceApprovalVO;
import my.hive_back.module.leave.ApprovalActionEnum;
import my.hive_back.module.sys.model.enums.PermissionCodeEnum;
import my.hive_back.module.user.mapper.UserMapper;
import my.hive_back.module.user.model.entity.User;
import my.hive_back.module.user.service.UserService;
import my.hive_back.module.wechat.service.WechatSubscribeNotificationService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
/**
 * FinanceApprovalService 属于小程序后端财务模块，实现核心业务编排与规则逻辑。
 */
@Service
public class FinanceApprovalService {

    private static final int STATUS_PENDING = 1;
    private static final int STATUS_APPROVED = 2;
    private static final int STATUS_REJECTED = 3;
    private static final int MAX_PARALLEL_APPROVERS = 30;

    @Resource
    private FinanceApprovalMapper financeApprovalMapper;

    @Resource
    private UserService userService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private CodeGeneratorUtil codeGeneratorUtil;

    @Resource
    private WechatSubscribeNotificationService wechatSubscribeNotificationService;

    @Transactional(rollbackFor = Exception.class)
    public String submit(FinanceSubmitRequest request) {
        Long userId = TenantPermissionContext.getUserId();
        String tenantCode = TenantPermissionContext.getTenantCode();
        Long managerId = userService.getManagerId(userId);

        FinanceApproval approval = new FinanceApproval();
        approval.setApprovalCode(codeGeneratorUtil.generateFinanceApprovalCode());
        approval.setTenantCode(tenantCode);
        approval.setApplyUserId(userId);
        approval.setCategory(request.getCategory().trim());
        approval.setAmount(request.getAmount());
        approval.setReason(request.getReason().trim());
        approval.setAttachmentName(blankToNull(request.getAttachmentName()));
        approval.setAttachmentUrl(InternalUploadUrlValidator.normalizeOptionalFinanceAttachment(request.getAttachmentUrl(), tenantCode));
        approval.setAttachmentSize(safeAttachmentSize(request.getAttachmentSize()));
        approval.setStatus(STATUS_PENDING);
        assignAuditors(approval, managerId);
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
                    .eq(FinanceApproval::getStatus, STATUS_PENDING);
            appendAuditorFilter(queryWrapper, userId);
        } else if ("others_pending".equalsIgnoreCase(scope)) {
            queryWrapper.ne(FinanceApproval::getApplyUserId, userId)
                    .eq(FinanceApproval::getStatus, STATUS_PENDING);
            appendAuditorFilter(queryWrapper, userId);
        } else if (!"all".equalsIgnoreCase(scope)) {
            appendAuditorFilter(queryWrapper, userId);
        }
        queryWrapper.orderByDesc(FinanceApproval::getCreateTime);
        return financeApprovalMapper.selectList(queryWrapper).stream().map(this::toVO).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void audit(FinanceAuditRequest request) {
        Long currentUserId = TenantPermissionContext.getUserId();
        FinanceApproval approval = getByCode(request.getApprovalCode());
        if (!canCurrentUserAudit(currentUserId, approval.getAuditorId(), approval.getAuditorIds())) {
            throw new BusinessException("您不是该财务审批单当前审批人");
        }
        if (approval.getStatus() != STATUS_PENDING) {
            throw new BusinessException("该财务审批单已处理，请勿重复审批");
        }

        Long previousAuditorId = approval.getAuditorId();
        String previousAuditorIds = approval.getAuditorIds();
        approval.setAuditComment(request.getComment());
        if (ApprovalActionEnum.APPROVE.getCode() == request.getAction()) {
            Long nextManagerId = userService.getManagerId(currentUserId);
            Integer roleLevel = userService.getRoleLevel(currentUserId);
            if (roleLevel != null && roleLevel >= 2 || nextManagerId == null) {
                approval.setStatus(STATUS_APPROVED);
            } else {
                assignAuditors(approval, nextManagerId);
            }
        } else {
            approval.setStatus(STATUS_REJECTED);
        }
        financeApprovalMapper.updateById(approval);
        notifyFinanceAuditChange(approval, previousAuditorId, previousAuditorIds);
    }

    private void notifyFinancePendingApprover(FinanceApproval approval) {
        for (Long auditorId : resolveNotifyAuditorIds(approval.getAuditorId(), approval.getAuditorIds())) {
            wechatSubscribeNotificationService.sendTodoAfterCommit(
                    auditorId,
                    "财务审批待处理",
                    buildApplicantName(approval.getApplyUserId()) + " 提交了财务单 " + approval.getApprovalCode(),
                    "/pages/approval/approval"
            );
        }
    }

    private void notifyFinanceAuditChange(FinanceApproval approval, Long previousAuditorId, String previousAuditorIds) {
        if (approval.getStatus() != null && approval.getStatus() == STATUS_PENDING
                && !sameAuditorGroup(previousAuditorId, previousAuditorIds, approval.getAuditorId(), approval.getAuditorIds())) {
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
        vo.setAuditorName(resolveAuditorNames(approval.getAuditorId(), approval.getAuditorIds()));
        vo.setStatusText(statusText(approval.getStatus()));
        return vo;
    }

    private void appendAuditorFilter(LambdaQueryWrapper<FinanceApproval> wrapper, Long userId) {
        if (userId == null) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.and(q -> q.eq(FinanceApproval::getAuditorId, userId)
                .or()
                .apply("FIND_IN_SET({0}, auditor_ids) > 0", String.valueOf(userId)));
    }

    private void assignAuditors(FinanceApproval approval, Long primaryAuditorId) {
        List<Long> auditorIds = resolveParallelAuditorIds(
                approval.getTenantCode(),
                approval.getApplyUserId(),
                primaryAuditorId,
                PermissionCodeEnum.CODE_APPROVAL_FINANCE_AUDIT
        );
        approval.setAuditorId(auditorIds.get(0));
        approval.setAuditorIds(joinAuditorIds(auditorIds));
    }

    private List<Long> resolveParallelAuditorIds(String tenantCode,
                                                 Long applyUserId,
                                                 Long primaryAuditorId,
                                                 String permissionCode) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        addCandidateAuditor(ids, primaryAuditorId, applyUserId);
        if (StringUtils.hasText(tenantCode) && StringUtils.hasText(permissionCode)) {
            List<Long> permissionAuditorIds = userMapper.selectActiveApproverIdsByPermission(tenantCode, permissionCode);
            if (permissionAuditorIds != null) {
                permissionAuditorIds.forEach(id -> addCandidateAuditor(ids, id, applyUserId));
            }
        }
        if (ids.isEmpty()) {
            throw new BusinessException("未找到可用审批人，请先配置直属负责人或审批角色权限");
        }
        return ids.stream().limit(MAX_PARALLEL_APPROVERS).toList();
    }

    private void addCandidateAuditor(LinkedHashSet<Long> ids, Long auditorId, Long applyUserId) {
        if (auditorId == null || auditorId <= 0) {
            return;
        }
        if (applyUserId != null && applyUserId.equals(auditorId)) {
            return;
        }
        ids.add(auditorId);
    }

    private boolean canCurrentUserAudit(Long currentUserId, Long auditorId, String auditorIds) {
        if (currentUserId == null) {
            return false;
        }
        if (currentUserId.equals(auditorId)) {
            return true;
        }
        return parseAuditorIds(auditorIds).contains(currentUserId);
    }

    private String resolveAuditorNames(Long auditorId, String auditorIds) {
        List<Long> ids = resolveNotifyAuditorIds(auditorId, auditorIds);
        List<String> names = new ArrayList<>();
        for (Long id : ids) {
            User auditor = userService.getUserById(id);
            if (auditor != null && StringUtils.hasText(auditor.getName())) {
                names.add(auditor.getName());
            }
        }
        return names.isEmpty() ? "待分配" : String.join("、", names);
    }

    private List<Long> resolveNotifyAuditorIds(Long auditorId, String auditorIds) {
        List<Long> ids = parseAuditorIds(auditorIds);
        if (ids.isEmpty() && auditorId != null) {
            ids = List.of(auditorId);
        }
        return ids;
    }

    private boolean sameAuditorGroup(Long previousAuditorId, String previousAuditorIds,
                                     Long currentAuditorId, String currentAuditorIds) {
        return resolveNotifyAuditorIds(previousAuditorId, previousAuditorIds)
                .equals(resolveNotifyAuditorIds(currentAuditorId, currentAuditorIds));
    }

    private List<Long> parseAuditorIds(String auditorIds) {
        if (!StringUtils.hasText(auditorIds)) {
            return List.of();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (String raw : auditorIds.split(",")) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            try {
                Long id = Long.valueOf(raw.trim());
                if (id > 0) {
                    ids.add(id);
                }
            } catch (NumberFormatException ignored) {
                // Ignore dirty historical data instead of breaking approval list rendering.
            }
        }
        return new ArrayList<>(ids);
    }

    private String joinAuditorIds(List<Long> auditorIds) {
        if (auditorIds == null || auditorIds.isEmpty()) {
            return null;
        }
        return String.join(",", auditorIds.stream().map(String::valueOf).toList());
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Long safeAttachmentSize(Long attachmentSize) {
        return attachmentSize == null || attachmentSize <= 0 ? null : attachmentSize;
    }
}

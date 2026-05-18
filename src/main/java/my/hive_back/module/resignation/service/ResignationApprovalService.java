package my.hive_back.module.resignation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive_back.common.utils.CodeGeneratorUtil;
import my.hive_back.module.approval.service.ApprovalAuditorCandidateService;
import my.hive_back.module.leave.ApprovalActionEnum;
import my.hive_back.module.resignation.mapper.ResignationApprovalMapper;
import my.hive_back.module.resignation.model.dto.ResignationAuditRequest;
import my.hive_back.module.resignation.model.dto.ResignationSubmitRequest;
import my.hive_back.module.resignation.model.entity.ResignationApproval;
import my.hive_back.module.resignation.model.vo.ResignationApprovalVO;
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
 * 小程序离职审批业务。
 */
@Service
public class ResignationApprovalService {

    private static final String APPROVAL_TYPE_RESIGNATION = "RESIGNATION";
    private static final int STATUS_PENDING = 1;
    private static final int STATUS_APPROVED = 2;
    private static final int STATUS_REJECTED = 3;
    private static final int MAX_PARALLEL_APPROVERS = 30;

    @Resource
    private ResignationApprovalMapper resignationApprovalMapper;

    @Resource
    private UserService userService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private CodeGeneratorUtil codeGeneratorUtil;

    @Resource
    private WechatSubscribeNotificationService wechatSubscribeNotificationService;

    @Resource
    private ApprovalAuditorCandidateService approvalAuditorCandidateService;

    @Transactional(rollbackFor = Exception.class)
    public String submit(ResignationSubmitRequest request) {
        Long userId = TenantPermissionContext.getUserId();
        String tenantCode = TenantPermissionContext.getTenantCode();
        Long managerId = userService.getManagerId(userId);
        Long pendingCount = resignationApprovalMapper.selectCount(new LambdaQueryWrapper<ResignationApproval>()
                .eq(ResignationApproval::getTenantCode, tenantCode)
                .eq(ResignationApproval::getApplyUserId, userId)
                .eq(ResignationApproval::getStatus, STATUS_PENDING));
        if (pendingCount != null && pendingCount > 0) {
            throw new BusinessException("已有待审批离职申请，请勿重复提交");
        }

        ResignationApproval approval = new ResignationApproval();
        approval.setResignationCode(codeGeneratorUtil.generateResignationApprovalCode());
        approval.setTenantCode(tenantCode);
        approval.setApplyUserId(userId);
        approval.setExpectedLeaveDate(request.getExpectedLeaveDate());
        approval.setReason(request.getReason().trim());
        approval.setHandoverNote(trimToNull(request.getHandoverNote()));
        approval.setStatus(STATUS_PENDING);
        assignAuditors(approval, managerId);
        resignationApprovalMapper.insert(approval);
        notifyPendingApprover(approval);
        return approval.getResignationCode();
    }

    public List<ResignationApprovalVO> list(String scope, Integer status) {
        Long userId = TenantPermissionContext.getUserId();
        String tenantCode = TenantPermissionContext.getTenantCode();
        if (tenantCode == null || tenantCode.isBlank()) {
            return List.of();
        }
        LambdaQueryWrapper<ResignationApproval> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ResignationApproval::getTenantCode, tenantCode);
        if (status != null) {
            queryWrapper.eq(ResignationApproval::getStatus, status);
        }
        if ("mine".equalsIgnoreCase(scope)) {
            queryWrapper.eq(ResignationApproval::getApplyUserId, userId);
        } else if ("self_pending".equalsIgnoreCase(scope)) {
            queryWrapper.eq(ResignationApproval::getApplyUserId, userId)
                    .eq(ResignationApproval::getStatus, STATUS_PENDING);
            appendAuditorFilter(queryWrapper, userId);
        } else if ("others_pending".equalsIgnoreCase(scope)) {
            queryWrapper.ne(ResignationApproval::getApplyUserId, userId)
                    .eq(ResignationApproval::getStatus, STATUS_PENDING);
            appendAuditorFilter(queryWrapper, userId);
        } else if (!"all".equalsIgnoreCase(scope)) {
            appendAuditorFilter(queryWrapper, userId);
        }
        queryWrapper.orderByDesc(ResignationApproval::getCreateTime);
        return resignationApprovalMapper.selectList(queryWrapper).stream().map(this::toVO).toList();
    }

    public ResignationApprovalVO detail(String resignationCode) {
        return toVO(getByCode(resignationCode));
    }

    @Transactional(rollbackFor = Exception.class)
    public void audit(ResignationAuditRequest request) {
        Long currentUserId = TenantPermissionContext.getUserId();
        ResignationApproval approval = getByCode(request.getResignationCode());
        if (!canCurrentUserAudit(currentUserId, approval.getAuditorId(), approval.getAuditorIds())) {
            throw new BusinessException("您不是该离职审批单当前审批人");
        }
        if (approval.getStatus() == null || approval.getStatus() != STATUS_PENDING) {
            throw new BusinessException("该离职审批单已处理，请勿重复审批");
        }

        Long previousAuditorId = approval.getAuditorId();
        String previousAuditorIds = approval.getAuditorIds();
        approval.setAuditComment(trimToNull(request.getComment()));
        if (ApprovalActionEnum.APPROVE.getCode() == request.getAction()) {
            Long nextManagerId = userService.getManagerId(currentUserId);
            Integer roleLevel = userService.getRoleLevel(currentUserId);
            if ((roleLevel != null && roleLevel >= 2) || nextManagerId == null) {
                approval.setStatus(STATUS_APPROVED);
                userService.markResignedByApproval(approval.getApplyUserId());
            } else {
                assignAuditors(approval, nextManagerId);
            }
        } else {
            approval.setStatus(STATUS_REJECTED);
        }
        if (approval.getStatus() == null || approval.getStatus() != STATUS_PENDING) {
            approvalAuditorCandidateService.closeActiveCandidates(
                    approval.getTenantCode(), APPROVAL_TYPE_RESIGNATION, approval.getResignationCode());
        }
        resignationApprovalMapper.updateById(approval);
        notifyAuditChange(approval, previousAuditorId, previousAuditorIds);
    }

    private ResignationApproval getByCode(String resignationCode) {
        String tenantCode = TenantPermissionContext.getTenantCode();
        ResignationApproval approval = resignationApprovalMapper.selectOne(new LambdaQueryWrapper<ResignationApproval>()
                .eq(ResignationApproval::getTenantCode, tenantCode)
                .eq(ResignationApproval::getResignationCode, resignationCode));
        if (approval == null) {
            throw new BusinessException("离职审批单不存在");
        }
        return approval;
    }

    private ResignationApprovalVO toVO(ResignationApproval approval) {
        ResignationApprovalVO vo = new ResignationApprovalVO();
        BeanUtils.copyProperties(approval, vo);
        vo.setStatusText(statusText(approval.getStatus()));
        User applyUser = userService.getUserById(approval.getApplyUserId());
        if (applyUser != null) {
            vo.setApplyUserName(applyUser.getName());
            vo.setApplyDepartmentName(applyUser.getDepartmentName());
        }
        vo.setAuditorName(resolveAuditorNames(approval.getAuditorId(), approval.getAuditorIds()));
        return vo;
    }

    private void notifyPendingApprover(ResignationApproval approval) {
        for (Long auditorId : resolveNotifyAuditorIds(approval.getAuditorId(), approval.getAuditorIds())) {
            wechatSubscribeNotificationService.sendTodoAfterCommit(
                    auditorId,
                    "离职审批待处理",
                    buildApplicantName(approval.getApplyUserId()) + " 提交了离职申请 " + approval.getResignationCode(),
                    "/pages/approval/approval?tab=resignation"
            );
        }
    }

    private void notifyAuditChange(ResignationApproval approval, Long previousAuditorId, String previousAuditorIds) {
        if (approval.getStatus() != null && approval.getStatus() == STATUS_PENDING
                && !sameAuditorGroup(previousAuditorId, previousAuditorIds, approval.getAuditorId(), approval.getAuditorIds())) {
            notifyPendingApprover(approval);
            return;
        }
        if (approval.getStatus() != null && (approval.getStatus() == STATUS_APPROVED || approval.getStatus() == STATUS_REJECTED)) {
            wechatSubscribeNotificationService.sendTodoAfterCommit(
                    approval.getApplyUserId(),
                    "离职审批结果",
                    "离职申请 " + approval.getResignationCode() + " " + statusText(approval.getStatus()),
                    "/pages/approval/approval?tab=resignation"
            );
        }
    }

    private void appendAuditorFilter(LambdaQueryWrapper<ResignationApproval> wrapper, Long userId) {
        if (userId == null) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.and(q -> q.eq(ResignationApproval::getAuditorId, userId)
                .or()
                .apply("FIND_IN_SET({0}, auditor_ids) > 0", String.valueOf(userId)));
    }

    private void assignAuditors(ResignationApproval approval, Long primaryAuditorId) {
        List<Long> auditorIds = resolveParallelAuditorIds(
                approval.getTenantCode(),
                approval.getApplyUserId(),
                primaryAuditorId,
                PermissionCodeEnum.CODE_APPROVAL_RESIGNATION_AUDIT
        );
        approval.setAuditorId(auditorIds.get(0));
        approval.setAuditorIds(joinAuditorIds(auditorIds));
        approvalAuditorCandidateService.replaceActiveCandidates(
                approval.getTenantCode(), APPROVAL_TYPE_RESIGNATION, approval.getResignationCode(), auditorIds);
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

    private String buildApplicantName(Long userId) {
        User user = userService.getUserById(userId);
        if (user == null || user.getName() == null || user.getName().isBlank()) {
            return "员工";
        }
        return user.getName();
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

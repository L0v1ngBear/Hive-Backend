package my.hive_back.module.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import my.hive.common.exception.BusinessException;
import my.hive_back.module.approval.mapper.ApprovalAuditorCandidateMapper;
import my.hive_back.module.approval.model.entity.ApprovalAuditorCandidate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class ApprovalAuditorCandidateService {

    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_CLOSED = 2;
    public static final int AUDIT_STATUS_PENDING = 0;
    public static final int AUDIT_STATUS_APPROVED = 1;
    public static final int AUDIT_STATUS_REJECTED = 2;

    @Resource
    private ApprovalAuditorCandidateMapper approvalAuditorCandidateMapper;

    public List<Long> findActiveAuditorIds(String tenantCode, String approvalType, String approvalCode) {
        if (!StringUtils.hasText(tenantCode) || !StringUtils.hasText(approvalType) || !StringUtils.hasText(approvalCode)) {
            return List.of();
        }
        List<ApprovalAuditorCandidate> rows = approvalAuditorCandidateMapper.selectList(
                new LambdaQueryWrapper<ApprovalAuditorCandidate>()
                        .eq(ApprovalAuditorCandidate::getApprovalType, approvalType)
                        .eq(ApprovalAuditorCandidate::getApprovalCode, approvalCode)
                        .eq(ApprovalAuditorCandidate::getStatus, STATUS_ACTIVE)
                        .orderByAsc(ApprovalAuditorCandidate::getId));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Long> auditorIds = new ArrayList<>();
        for (ApprovalAuditorCandidate row : rows) {
            if (row.getAuditorId() != null && row.getAuditorId() > 0 && !auditorIds.contains(row.getAuditorId())) {
                auditorIds.add(row.getAuditorId());
            }
        }
        return auditorIds;
    }

    public List<Long> findPendingAuditorIds(String tenantCode, String approvalType, String approvalCode) {
        if (!StringUtils.hasText(tenantCode) || !StringUtils.hasText(approvalType) || !StringUtils.hasText(approvalCode)) {
            return List.of();
        }
        List<ApprovalAuditorCandidate> rows = approvalAuditorCandidateMapper.selectList(
                new LambdaQueryWrapper<ApprovalAuditorCandidate>()
                        .eq(ApprovalAuditorCandidate::getApprovalType, approvalType)
                        .eq(ApprovalAuditorCandidate::getApprovalCode, approvalCode)
                        .eq(ApprovalAuditorCandidate::getStatus, STATUS_ACTIVE)
                        .eq(ApprovalAuditorCandidate::getAuditStatus, AUDIT_STATUS_PENDING)
                        .orderByAsc(ApprovalAuditorCandidate::getId));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Long> auditorIds = new ArrayList<>();
        for (ApprovalAuditorCandidate row : rows) {
            if (row.getAuditorId() != null && row.getAuditorId() > 0 && !auditorIds.contains(row.getAuditorId())) {
                auditorIds.add(row.getAuditorId());
            }
        }
        return auditorIds;
    }

    public boolean isPendingAuditor(String tenantCode, String approvalType, String approvalCode, Long auditorId) {
        if (!StringUtils.hasText(tenantCode) || !StringUtils.hasText(approvalType)
                || !StringUtils.hasText(approvalCode) || auditorId == null || auditorId <= 0) {
            return false;
        }
        Long count = approvalAuditorCandidateMapper.selectCount(new LambdaQueryWrapper<ApprovalAuditorCandidate>()
                .eq(ApprovalAuditorCandidate::getApprovalType, approvalType)
                .eq(ApprovalAuditorCandidate::getApprovalCode, approvalCode)
                .eq(ApprovalAuditorCandidate::getAuditorId, auditorId)
                .eq(ApprovalAuditorCandidate::getStatus, STATUS_ACTIVE)
                .eq(ApprovalAuditorCandidate::getAuditStatus, AUDIT_STATUS_PENDING));
        return count != null && count > 0;
    }

    public boolean hasPendingAuditors(String tenantCode, String approvalType, String approvalCode) {
        return !findPendingAuditorIds(tenantCode, approvalType, approvalCode).isEmpty();
    }

    public boolean hasRejectedAuditors(String tenantCode, String approvalType, String approvalCode) {
        if (!StringUtils.hasText(tenantCode) || !StringUtils.hasText(approvalType)
                || !StringUtils.hasText(approvalCode)) {
            return false;
        }
        Long count = approvalAuditorCandidateMapper.selectCount(new LambdaQueryWrapper<ApprovalAuditorCandidate>()
                .eq(ApprovalAuditorCandidate::getApprovalType, approvalType)
                .eq(ApprovalAuditorCandidate::getApprovalCode, approvalCode)
                .eq(ApprovalAuditorCandidate::getStatus, STATUS_ACTIVE)
                .eq(ApprovalAuditorCandidate::getAuditStatus, AUDIT_STATUS_REJECTED));
        return count != null && count > 0;
    }

    public List<String> findPendingApprovalCodes(String tenantCode, String approvalType, Long auditorId) {
        if (!StringUtils.hasText(tenantCode) || !StringUtils.hasText(approvalType) || auditorId == null || auditorId <= 0) {
            return List.of();
        }
        List<ApprovalAuditorCandidate> rows = approvalAuditorCandidateMapper.selectList(
                new LambdaQueryWrapper<ApprovalAuditorCandidate>()
                        .eq(ApprovalAuditorCandidate::getApprovalType, approvalType)
                        .eq(ApprovalAuditorCandidate::getAuditorId, auditorId)
                        .eq(ApprovalAuditorCandidate::getStatus, STATUS_ACTIVE)
                        .eq(ApprovalAuditorCandidate::getAuditStatus, AUDIT_STATUS_PENDING)
                        .orderByDesc(ApprovalAuditorCandidate::getId));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (ApprovalAuditorCandidate row : rows) {
            if (StringUtils.hasText(row.getApprovalCode())) {
                codes.add(row.getApprovalCode());
            }
        }
        return new ArrayList<>(codes);
    }

    public boolean markAuditorDecision(String tenantCode,
                                       String approvalType,
                                       String approvalCode,
                                       Long auditorId,
                                       boolean approved,
                                       String comment) {
        if (!StringUtils.hasText(tenantCode) || !StringUtils.hasText(approvalType)
                || !StringUtils.hasText(approvalCode) || auditorId == null || auditorId <= 0) {
            return false;
        }
        int rows = approvalAuditorCandidateMapper.update(null, new LambdaUpdateWrapper<ApprovalAuditorCandidate>()
                .eq(ApprovalAuditorCandidate::getApprovalType, approvalType)
                .eq(ApprovalAuditorCandidate::getApprovalCode, approvalCode)
                .eq(ApprovalAuditorCandidate::getAuditorId, auditorId)
                .eq(ApprovalAuditorCandidate::getStatus, STATUS_ACTIVE)
                .eq(ApprovalAuditorCandidate::getAuditStatus, AUDIT_STATUS_PENDING)
                .set(ApprovalAuditorCandidate::getAuditStatus, approved ? AUDIT_STATUS_APPROVED : AUDIT_STATUS_REJECTED)
                .set(ApprovalAuditorCandidate::getAuditComment, comment)
                .set(ApprovalAuditorCandidate::getAuditTime, LocalDateTime.now())
                .set(ApprovalAuditorCandidate::getUpdateTime, LocalDateTime.now()));
        if (rows != 1) {
            throw new BusinessException(409, "审批决定已被其他操作处理，请刷新后重试");
        }
        return true;
    }

    public void createActiveCandidates(String tenantCode,
                                       String approvalType,
                                       String approvalCode,
                                       List<Long> auditorIds) {
        if (!findActiveAuditorIds(tenantCode, approvalType, approvalCode).isEmpty()) {
            throw new BusinessException(409, "该审批已有活动候选人，不能重复提交");
        }
        insertActiveCandidates(tenantCode, approvalType, approvalCode, auditorIds);
    }

    public void replaceActiveCandidates(String tenantCode, String approvalType, String approvalCode, List<Long> auditorIds) {
        closeActiveCandidates(tenantCode, approvalType, approvalCode);
        insertActiveCandidates(tenantCode, approvalType, approvalCode, auditorIds);
    }

    private void insertActiveCandidates(String tenantCode,
                                        String approvalType,
                                        String approvalCode,
                                        List<Long> auditorIds) {
        if (!StringUtils.hasText(tenantCode) || !StringUtils.hasText(approvalType)
                || !StringUtils.hasText(approvalCode) || auditorIds == null || auditorIds.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(auditorIds);
        LocalDateTime now = LocalDateTime.now();
        for (Long auditorId : uniqueIds) {
            if (auditorId == null || auditorId <= 0) {
                continue;
            }
            ApprovalAuditorCandidate candidate = new ApprovalAuditorCandidate();
            candidate.setTenantCode(tenantCode);
            candidate.setApprovalType(approvalType);
            candidate.setApprovalCode(approvalCode);
            candidate.setAuditorId(auditorId);
            candidate.setStatus(STATUS_ACTIVE);
            candidate.setAuditStatus(AUDIT_STATUS_PENDING);
            candidate.setCreateTime(now);
            candidate.setUpdateTime(now);
            approvalAuditorCandidateMapper.insert(candidate);
        }
    }

    public void closeActiveCandidates(String tenantCode, String approvalType, String approvalCode) {
        if (!StringUtils.hasText(tenantCode) || !StringUtils.hasText(approvalType) || !StringUtils.hasText(approvalCode)) {
            return;
        }
        approvalAuditorCandidateMapper.update(null, new LambdaUpdateWrapper<ApprovalAuditorCandidate>()
                .eq(ApprovalAuditorCandidate::getApprovalType, approvalType)
                .eq(ApprovalAuditorCandidate::getApprovalCode, approvalCode)
                .eq(ApprovalAuditorCandidate::getStatus, STATUS_ACTIVE)
                .set(ApprovalAuditorCandidate::getStatus, STATUS_CLOSED)
                .set(ApprovalAuditorCandidate::getUpdateTime, LocalDateTime.now()));
    }
}

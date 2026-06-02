package my.hive_back.module.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
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

    @Resource
    private ApprovalAuditorCandidateMapper approvalAuditorCandidateMapper;

    public List<Long> findActiveAuditorIds(String tenantCode, String approvalType, String approvalCode) {
        if (!StringUtils.hasText(tenantCode) || !StringUtils.hasText(approvalType) || !StringUtils.hasText(approvalCode)) {
            return List.of();
        }
        List<ApprovalAuditorCandidate> rows = approvalAuditorCandidateMapper.selectList(
                new LambdaQueryWrapper<ApprovalAuditorCandidate>()
                        .eq(ApprovalAuditorCandidate::getTenantCode, tenantCode)
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

    public void replaceActiveCandidates(String tenantCode, String approvalType, String approvalCode, List<Long> auditorIds) {
        closeActiveCandidates(tenantCode, approvalType, approvalCode);
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
                .eq(ApprovalAuditorCandidate::getTenantCode, tenantCode)
                .eq(ApprovalAuditorCandidate::getApprovalType, approvalType)
                .eq(ApprovalAuditorCandidate::getApprovalCode, approvalCode)
                .eq(ApprovalAuditorCandidate::getStatus, STATUS_ACTIVE)
                .set(ApprovalAuditorCandidate::getStatus, STATUS_CLOSED)
                .set(ApprovalAuditorCandidate::getUpdateTime, LocalDateTime.now()));
    }
}

package my.hive_back.module.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive_back.module.approval.mapper.ApprovalDefaultAuditorMapper;
import my.hive_back.module.approval.model.entity.ApprovalDefaultAuditor;
import my.hive_back.module.approval.model.vo.ApprovalAuditorOptionVO;
import my.hive_back.module.sys.model.enums.PermissionCodeEnum;
import my.hive_back.module.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class ApprovalDefaultAuditorService {

    public static final String TYPE_LEAVE = "LEAVE";
    public static final String TYPE_FINANCE = "FINANCE";
    public static final String TYPE_RESIGNATION = "RESIGNATION";
    public static final String TYPE_ORDER = "ORDER";
    private static final int STATUS_ACTIVE = 1;

    @Resource
    private ApprovalDefaultAuditorMapper approvalDefaultAuditorMapper;

    @Resource
    private UserMapper userMapper;

    public Long resolveAuditorId(String tenantCode,
                                 String approvalType,
                                 Long applyUserId,
                                 Long specifiedAuditorId,
                                 String permissionCode,
                                 boolean strictSpecified) {
        String normalizedType = normalizeType(approvalType);
        List<Long> permissionAuditorIds = StringUtils.hasText(tenantCode) && StringUtils.hasText(permissionCode)
                ? userMapper.selectActiveApproverIdsByPermission(tenantCode, permissionCode)
                : List.of();
        Long specified = normalizeAuditorId(specifiedAuditorId);
        if (specified != null) {
            validateNotSelf(applyUserId, specified);
            if (permissionAuditorIds.contains(specified)) {
                return specified;
            }
            if (strictSpecified) {
                throw new BusinessException("所选审批人没有对应审批权限，请重新选择");
            }
        }

        Long defaultAuditorId = findActiveAuditorId(tenantCode, normalizedType);
        if (defaultAuditorId != null) {
            validateNotSelf(applyUserId, defaultAuditorId);
            if (permissionAuditorIds.contains(defaultAuditorId)) {
                return defaultAuditorId;
            }
        }

        return permissionAuditorIds.stream()
                .filter(id -> id != null && id > 0)
                .filter(id -> applyUserId == null || !applyUserId.equals(id))
                .findFirst()
                .orElseThrow(() -> new BusinessException("未找到可用审批人，请先配置默认审批人或审批角色权限"));
    }

    public List<ApprovalAuditorOptionVO> applyDefaultMark(String approvalType, List<ApprovalAuditorOptionVO> options) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }
        Long defaultAuditorId = findActiveAuditorId(TenantPermissionContext.getTenantCode(), normalizeType(approvalType));
        options.forEach(item -> item.setDefaultAuditor(defaultAuditorId != null && defaultAuditorId.equals(item.getId())));
        return options.stream()
                .sorted(Comparator.comparing((ApprovalAuditorOptionVO item) -> !Boolean.TRUE.equals(item.getDefaultAuditor()))
                        .thenComparing(item -> item.getId() == null ? Long.MAX_VALUE : item.getId()))
                .toList();
    }

    public String normalizeType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "LEAVE" -> TYPE_LEAVE;
            case "FINANCE" -> TYPE_FINANCE;
            case "RESIGNATION" -> TYPE_RESIGNATION;
            case "ORDER", "ORDER_SALES", "ORDER_PRODUCTION", "SALES", "PRODUCTION" -> TYPE_ORDER;
            default -> throw new BusinessException("审批类型不合法");
        };
    }

    public String permissionCode(String approvalType) {
        return switch (normalizeType(approvalType)) {
            case TYPE_LEAVE -> PermissionCodeEnum.CODE_APPROVAL_LEAVE_AUDIT;
            case TYPE_FINANCE -> PermissionCodeEnum.CODE_APPROVAL_FINANCE_AUDIT;
            case TYPE_RESIGNATION -> PermissionCodeEnum.CODE_APPROVAL_RESIGNATION_AUDIT;
            case TYPE_ORDER -> PermissionCodeEnum.CODE_SALES_ORDER_STATUS;
            default -> throw new BusinessException("审批类型不合法");
        };
    }

    private Long findActiveAuditorId(String tenantCode, String type) {
        if (!StringUtils.hasText(tenantCode)) {
            return null;
        }
        ApprovalDefaultAuditor entity = approvalDefaultAuditorMapper.selectOne(new LambdaQueryWrapper<ApprovalDefaultAuditor>()
                .eq(ApprovalDefaultAuditor::getTenantCode, tenantCode)
                .eq(ApprovalDefaultAuditor::getApprovalType, normalizeType(type))
                .eq(ApprovalDefaultAuditor::getStatus, STATUS_ACTIVE)
                .last("LIMIT 1"));
        return entity == null ? null : entity.getAuditorId();
    }

    private Long normalizeAuditorId(Long auditorId) {
        return auditorId == null || auditorId <= 0 ? null : auditorId;
    }

    private void validateNotSelf(Long applyUserId, Long auditorId) {
        if (applyUserId != null && applyUserId.equals(auditorId)) {
            throw new BusinessException("审批人不能选择申请人本人");
        }
    }
}

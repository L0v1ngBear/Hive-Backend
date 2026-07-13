package my.hive_back.module.approval.service;

import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive_back.module.sys.model.enums.PermissionCodeEnum;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Enforces approval list scopes and row-level access independently of the client UI.
 */
@Service
public class ApprovalAccessService {

    public String requireListScope(Type type, String requestedScope) {
        String scope = normalizeScope(requestedScope);
        if ("mine".equals(scope)) {
            if (canCreate(type) || canReview(type)) {
                return scope;
            }
            deny(type, "查看本人记录");
        }
        if ("all".equals(scope)) {
            if (TenantPermissionContext.hasPermission(type.listPermission)) {
                return scope;
            }
            deny(type, "查看全部记录");
        }
        if (canReview(type)) {
            return scope;
        }
        deny(type, "查看待处理记录");
        return scope;
    }

    public void requireDetailAccess(Type type,
                                    Long applyUserId,
                                    Long auditorId,
                                    String auditorIds) {
        Long currentUserId = TenantPermissionContext.getUserId();
        if (currentUserId != null && (currentUserId.equals(applyUserId)
                || currentUserId.equals(auditorId)
                || containsAuditor(auditorIds, currentUserId)
                || TenantPermissionContext.hasPermission(type.listPermission))) {
            return;
        }
        deny(type, "查看该记录");
    }

    public boolean canCreate(Type type) {
        return TenantPermissionContext.hasPermission(type.submitPermission);
    }

    public boolean canReview(Type type) {
        return TenantPermissionContext.hasPermission(type.listPermission)
                || TenantPermissionContext.hasPermission(type.auditPermission);
    }

    public boolean canViewOrder() {
        return TenantPermissionContext.hasPermission(PermissionCodeEnum.CODE_ORDER_LIST);
    }

    public boolean canViewQuality() {
        return TenantPermissionContext.hasPermission(PermissionCodeEnum.CODE_BADPRODUCT_PROCESS);
    }

    private String normalizeScope(String requestedScope) {
        if (!StringUtils.hasText(requestedScope)) {
            return "pending";
        }
        String normalized = requestedScope.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "mine", "pending", "self_pending", "others_pending", "all" -> normalized;
            default -> "pending";
        };
    }

    private boolean containsAuditor(String auditorIds, Long currentUserId) {
        if (!StringUtils.hasText(auditorIds) || currentUserId == null) {
            return false;
        }
        for (String raw : auditorIds.split(",")) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            try {
                if (currentUserId.equals(Long.valueOf(raw.trim()))) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // Historical dirty values do not widen access.
            }
        }
        return false;
    }

    private void deny(Type type, String action) {
        throw new BusinessException(403, "您没有权限" + action + type.label + "审批");
    }

    public enum Type {
        LEAVE("请假",
                PermissionCodeEnum.CODE_APPROVAL_LEAVE_SUBMIT,
                PermissionCodeEnum.CODE_APPROVAL_LEAVE,
                PermissionCodeEnum.CODE_APPROVAL_LEAVE_AUDIT),
        FINANCE("财务",
                PermissionCodeEnum.CODE_APPROVAL_FINANCE_SUBMIT,
                PermissionCodeEnum.CODE_APPROVAL_FINANCE,
                PermissionCodeEnum.CODE_APPROVAL_FINANCE_AUDIT),
        RESIGNATION("离职",
                PermissionCodeEnum.CODE_APPROVAL_RESIGNATION_SUBMIT,
                PermissionCodeEnum.CODE_APPROVAL_RESIGNATION,
                PermissionCodeEnum.CODE_APPROVAL_RESIGNATION_AUDIT);

        private final String label;
        private final String submitPermission;
        private final String listPermission;
        private final String auditPermission;

        Type(String label, String submitPermission, String listPermission, String auditPermission) {
            this.label = label;
            this.submitPermission = submitPermission;
            this.listPermission = listPermission;
            this.auditPermission = auditPermission;
        }
    }
}

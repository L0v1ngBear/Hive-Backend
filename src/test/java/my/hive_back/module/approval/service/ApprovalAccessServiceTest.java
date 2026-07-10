package my.hive_back.module.approval.service;

import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive_back.module.sys.model.enums.PermissionCodeEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApprovalAccessServiceTest {

    private final ApprovalAccessService service = new ApprovalAccessService();

    @AfterEach
    void clearContext() {
        TenantPermissionContext.clear();
    }

    @Test
    void submitterCanOnlyRequestOwnRows() {
        TenantPermissionContext.init("TENANT-TEST", 7L,
                Set.of(PermissionCodeEnum.CODE_APPROVAL_LEAVE_SUBMIT));

        assertEquals("mine", service.requireListScope(ApprovalAccessService.Type.LEAVE, "mine"));
        assertThrows(BusinessException.class,
                () -> service.requireListScope(ApprovalAccessService.Type.LEAVE, "pending"));
        assertThrows(BusinessException.class,
                () -> service.requireListScope(ApprovalAccessService.Type.LEAVE, "all"));
        assertThrows(BusinessException.class,
                () -> service.requireListScope(ApprovalAccessService.Type.LEAVE, "unknown"));
    }

    @Test
    void reviewerScopeDependsOnListAndAuditPermissions() {
        TenantPermissionContext.init("TENANT-TEST", 7L,
                Set.of(PermissionCodeEnum.CODE_APPROVAL_FINANCE_AUDIT));

        assertEquals("pending", service.requireListScope(ApprovalAccessService.Type.FINANCE, null));
        assertEquals("others_pending",
                service.requireListScope(ApprovalAccessService.Type.FINANCE, "others_pending"));
        assertThrows(BusinessException.class,
                () -> service.requireListScope(ApprovalAccessService.Type.FINANCE, "all"));

        TenantPermissionContext.init("TENANT-TEST", 7L,
                Set.of(PermissionCodeEnum.CODE_APPROVAL_FINANCE));
        assertEquals("all", service.requireListScope(ApprovalAccessService.Type.FINANCE, "all"));
    }

    @Test
    void detailAccessIsLimitedToApplicantAssignedAuditorOrBroadViewer() {
        TenantPermissionContext.init("TENANT-TEST", 7L,
                Set.of(PermissionCodeEnum.CODE_APPROVAL_RESIGNATION_DETAIL));
        assertDoesNotThrow(() -> service.requireDetailAccess(
                ApprovalAccessService.Type.RESIGNATION, 7L, 8L, "8,9"));
        assertThrows(BusinessException.class, () -> service.requireDetailAccess(
                ApprovalAccessService.Type.RESIGNATION, 6L, 8L, "8,9"));

        TenantPermissionContext.init("TENANT-TEST", 7L,
                Set.of(PermissionCodeEnum.CODE_APPROVAL_RESIGNATION_AUDIT));
        assertDoesNotThrow(() -> service.requireDetailAccess(
                ApprovalAccessService.Type.RESIGNATION, 6L, 7L, null));

        TenantPermissionContext.init("TENANT-TEST", 7L,
                Set.of(PermissionCodeEnum.CODE_APPROVAL_RESIGNATION));
        assertDoesNotThrow(() -> service.requireDetailAccess(
                ApprovalAccessService.Type.RESIGNATION, 6L, 8L, "8,9"));
    }
}

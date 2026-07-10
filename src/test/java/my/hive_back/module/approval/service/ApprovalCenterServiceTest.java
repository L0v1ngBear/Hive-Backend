package my.hive_back.module.approval.service;

import my.hive.common.context.TenantPermissionContext;
import my.hive_back.module.approval.model.vo.ApprovalSummaryVO;
import my.hive_back.module.finance.mapper.FinanceApprovalMapper;
import my.hive_back.module.leave.mapper.LeaveMapper;
import my.hive_back.module.order.mapper.ProductionOrderMapper;
import my.hive_back.module.order.mapper.SalesOrderMapper;
import my.hive_back.module.resignation.mapper.ResignationApprovalMapper;
import my.hive_back.module.sys.model.enums.PermissionCodeEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalCenterServiceTest {

    @Mock
    private LeaveMapper leaveMapper;

    @Mock
    private FinanceApprovalMapper financeApprovalMapper;

    @Mock
    private ResignationApprovalMapper resignationApprovalMapper;

    @Mock
    private SalesOrderMapper salesOrderMapper;

    @Mock
    private ProductionOrderMapper productionOrderMapper;

    @Mock
    private ApprovalAuditorCandidateService approvalAuditorCandidateService;

    @InjectMocks
    private ApprovalCenterService service;

    @BeforeEach
    void stubEmptyApprovalData() {
        when(leaveMapper.selectCount(any())).thenReturn(0L);
        when(financeApprovalMapper.selectCount(any())).thenReturn(0L);
        when(resignationApprovalMapper.selectCount(any())).thenReturn(0L);
        when(salesOrderMapper.selectList(any())).thenReturn(List.of());
        when(productionOrderMapper.selectList(any())).thenReturn(List.of());
        when(approvalAuditorCandidateService.findPendingApprovalCodes(any(), any(), any())).thenReturn(List.of());
    }

    @AfterEach
    void clearPermissionContext() {
        TenantPermissionContext.clear();
    }

    @Test
    void summaryExposesOnlyGrantedCreateCapabilities() {
        TenantPermissionContext.init("TENANT-TEST", 1L, Set.of(
                PermissionCodeEnum.CODE_APPROVAL_FINANCE_SUBMIT,
                PermissionCodeEnum.CODE_APPROVAL_LEAVE_SUBMIT));

        ApprovalSummaryVO summary = service.summary();

        assertTrue(summary.isCanCreateFinance());
        assertTrue(summary.isCanCreateLeave());
        assertFalse(summary.isCanCreateResignation());
    }

    @Test
    void summaryDisablesCreateCapabilitiesWithoutPermissions() {
        TenantPermissionContext.init("TENANT-TEST", 1L, Set.of());

        ApprovalSummaryVO summary = service.summary();

        assertFalse(summary.isCanCreateFinance());
        assertFalse(summary.isCanCreateLeave());
        assertFalse(summary.isCanCreateResignation());
    }
}

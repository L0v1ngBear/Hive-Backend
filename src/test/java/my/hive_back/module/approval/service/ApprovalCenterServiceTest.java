package my.hive_back.module.approval.service;

import my.hive.common.context.TenantPermissionContext;
import my.hive_back.module.approval.model.dto.OrderApprovalAuditRequest;
import my.hive_back.module.approval.model.vo.ApprovalSummaryVO;
import my.hive_back.module.finance.mapper.FinanceApprovalMapper;
import my.hive_back.module.leave.mapper.LeaveMapper;
import my.hive_back.module.order.mapper.ProductionOrderMapper;
import my.hive_back.module.order.mapper.SalesOrderMapper;
import my.hive_back.module.order.OrderStatusEnum;
import my.hive_back.module.order.model.entity.SalesOrder;
import my.hive_back.module.order.service.SalesOrderService;
import my.hive_back.module.resignation.mapper.ResignationApprovalMapper;
import my.hive_back.module.sys.model.enums.PermissionCodeEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
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

    @Mock
    private SalesOrderService salesOrderService;

    @Spy
    private ApprovalAccessService approvalAccessService = new ApprovalAccessService();

    @InjectMocks
    private ApprovalCenterService service;

    @BeforeEach
    void stubEmptyApprovalData() {
        lenient().when(leaveMapper.selectCount(any())).thenReturn(0L);
        lenient().when(financeApprovalMapper.selectCount(any())).thenReturn(0L);
        lenient().when(resignationApprovalMapper.selectCount(any())).thenReturn(0L);
        lenient().when(salesOrderMapper.selectList(any())).thenReturn(List.of());
        lenient().when(productionOrderMapper.selectList(any())).thenReturn(List.of());
        lenient().when(approvalAuditorCandidateService.findPendingApprovalCodes(any(), any(), any())).thenReturn(List.of());
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

    @Test
    void summaryExposesOnlyGrantedViewAndReviewCapabilities() {
        TenantPermissionContext.init("TENANT-TEST", 1L, Set.of(
                PermissionCodeEnum.CODE_ORDER_LIST,
                PermissionCodeEnum.CODE_BADPRODUCT_PROCESS,
                PermissionCodeEnum.CODE_APPROVAL_FINANCE_AUDIT,
                PermissionCodeEnum.CODE_APPROVAL_LEAVE));

        ApprovalSummaryVO summary = service.summary();

        assertTrue(summary.isCanViewOrder());
        assertTrue(summary.isCanViewQuality());
        assertTrue(summary.isCanReviewFinance());
        assertTrue(summary.isCanReviewLeave());
        assertFalse(summary.isCanReviewResignation());
    }

    @Test
    void approvingShipmentApprovalAdvancesOrderOnlyAfterCandidateDecisionsComplete() {
        TenantPermissionContext.init("TENANT-TEST", 2L, Set.of());
        SalesOrder order = shipmentPendingOrder();
        when(salesOrderMapper.selectOne(any())).thenReturn(order);
        when(salesOrderService.hasPendingSalesShipmentApproval(order.getOrderId())).thenReturn(true);
        when(approvalAuditorCandidateService.findPendingAuditorIds("TENANT-TEST", "ORDER", "sales:SO-SHIP-001"))
                .thenReturn(List.of(2L));
        when(approvalAuditorCandidateService.isPendingAuditor("TENANT-TEST", "ORDER", "sales:SO-SHIP-001", 2L))
                .thenReturn(true);
        when(approvalAuditorCandidateService.markAuditorDecision(
                "TENANT-TEST", "ORDER", "sales:SO-SHIP-001", 2L, true, "approved"))
                .thenReturn(true);
        when(approvalAuditorCandidateService.hasPendingAuditors("TENANT-TEST", "ORDER", "sales:SO-SHIP-001"))
                .thenReturn(false);

        service.audit(orderAuditRequest(1, "approved"));

        verify(salesOrderService).approveShipment("SO-SHIP-001", "approved");
        verify(approvalAuditorCandidateService).closeActiveCandidates("TENANT-TEST", "ORDER", "sales:SO-SHIP-001");
    }

    @Test
    void rejectingShipmentApprovalLeavesOrderPendingShip() {
        TenantPermissionContext.init("TENANT-TEST", 2L, Set.of());
        SalesOrder order = shipmentPendingOrder();
        when(salesOrderMapper.selectOne(any())).thenReturn(order);
        when(salesOrderService.hasPendingSalesShipmentApproval(order.getOrderId())).thenReturn(true);
        when(approvalAuditorCandidateService.findPendingAuditorIds("TENANT-TEST", "ORDER", "sales:SO-SHIP-001"))
                .thenReturn(List.of(2L));
        when(approvalAuditorCandidateService.isPendingAuditor("TENANT-TEST", "ORDER", "sales:SO-SHIP-001", 2L))
                .thenReturn(true);
        when(approvalAuditorCandidateService.markAuditorDecision(
                "TENANT-TEST", "ORDER", "sales:SO-SHIP-001", 2L, false, "rejected"))
                .thenReturn(true);

        service.audit(orderAuditRequest(2, "rejected"));

        assertTrue(OrderStatusEnum.PENDING_SHIP.getCode().equals(order.getStatus()));
        verify(salesOrderService, never()).approveShipment(any(), any());
        verify(approvalAuditorCandidateService).closeActiveCandidates("TENANT-TEST", "ORDER", "sales:SO-SHIP-001");
    }

    @Test
    void approvingShipmentApprovalWaitsForEveryCandidateDecision() {
        TenantPermissionContext.init("TENANT-TEST", 2L, Set.of());
        SalesOrder order = shipmentPendingOrder();
        when(salesOrderMapper.selectOne(any())).thenReturn(order);
        when(salesOrderService.hasPendingSalesShipmentApproval(order.getOrderId())).thenReturn(true);
        when(approvalAuditorCandidateService.findPendingAuditorIds("TENANT-TEST", "ORDER", "sales:SO-SHIP-001"))
                .thenReturn(List.of(2L, 3L));
        when(approvalAuditorCandidateService.isPendingAuditor("TENANT-TEST", "ORDER", "sales:SO-SHIP-001", 2L))
                .thenReturn(true);
        when(approvalAuditorCandidateService.markAuditorDecision(
                "TENANT-TEST", "ORDER", "sales:SO-SHIP-001", 2L, true, "approved"))
                .thenReturn(true);
        when(approvalAuditorCandidateService.hasPendingAuditors("TENANT-TEST", "ORDER", "sales:SO-SHIP-001"))
                .thenReturn(true);

        service.audit(orderAuditRequest(1, "approved"));

        verify(salesOrderService, never()).approveShipment(any(), any());
        verify(approvalAuditorCandidateService, never()).closeActiveCandidates(any(), any(), any());
    }

    private SalesOrder shipmentPendingOrder() {
        SalesOrder order = new SalesOrder();
        order.setOrderId("SO-SHIP-001");
        order.setTenantCode("TENANT-TEST");
        order.setStatus(OrderStatusEnum.PENDING_SHIP.getCode());
        return order;
    }

    private OrderApprovalAuditRequest orderAuditRequest(int action, String comment) {
        OrderApprovalAuditRequest request = new OrderApprovalAuditRequest();
        request.setOrderType("sales");
        request.setOrderId("SO-SHIP-001");
        request.setAction(action);
        request.setComment(comment);
        return request;
    }
}

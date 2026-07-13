package my.hive_back.order;

import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive.common.order.OrderFlowCodeUtil;
import my.hive_back.module.approval.service.ApprovalAuditorCandidateService;
import my.hive_back.module.approval.service.ApprovalDefaultAuditorService;
import my.hive_back.module.installation.service.InstallationTaskSyncService;
import my.hive_back.module.order.OrderCategoryEnum;
import my.hive_back.module.order.OrderStatusEnum;
import my.hive_back.module.order.mapper.SalesOrderMapper;
import my.hive_back.module.order.mapper.SalesOrderStatusLogMapper;
import my.hive_back.module.order.model.dto.SalesOrderUpdateRequest;
import my.hive_back.module.order.model.entity.SalesOrder;
import my.hive_back.module.order.model.entity.SalesOrderStatusLog;
import my.hive_back.module.order.service.ProductionOrderService;
import my.hive_back.module.order.service.SalesOrderService;
import my.hive_back.module.user.mapper.UserMapper;
import my.hive_back.module.wechat.service.WechatSubscribeNotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesOrderFlowAdvanceTest {

    private static final String TENANT_CODE = "TENANT-TEST";
    private static final String ORDER_ID = "SO-20260713-001";
    private static final String FLOW_SECRET = "drawing-budget-flow-test-secret";

    @Mock
    private SalesOrderMapper salesOrderMapper;

    @Mock
    private SalesOrderStatusLogMapper salesOrderStatusLogMapper;

    @Mock
    private ProductionOrderService productionOrderService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private WechatSubscribeNotificationService wechatSubscribeNotificationService;

    @Mock
    private InstallationTaskSyncService installationTaskSyncService;

    @Mock
    private ApprovalAuditorCandidateService approvalAuditorCandidateService;

    @Mock
    private ApprovalDefaultAuditorService approvalDefaultAuditorService;

    private SalesOrderService service;

    @BeforeEach
    void setUp() {
        service = new SalesOrderService();
        ReflectionTestUtils.setField(service, "salesOrderMapper", salesOrderMapper);
        ReflectionTestUtils.setField(service, "salesOrderStatusLogMapper", salesOrderStatusLogMapper);
        ReflectionTestUtils.setField(service, "productionOrderService", productionOrderService);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "wechatSubscribeNotificationService", wechatSubscribeNotificationService);
        ReflectionTestUtils.setField(service, "installationTaskSyncService", installationTaskSyncService);
        ReflectionTestUtils.setField(service, "approvalAuditorCandidateService", approvalAuditorCandidateService);
        ReflectionTestUtils.setField(service, "approvalDefaultAuditorService", approvalDefaultAuditorService);
        ReflectionTestUtils.setField(service, "orderFlowCodeSecret", FLOW_SECRET);
    }

    @AfterEach
    void clearContext() {
        TenantPermissionContext.clear();
    }

    @Test
    void drawingBudgetScanCompletesBudgetAndTreatsBudgetCompletedAsTerminal() {
        TenantPermissionContext.init(TENANT_CODE, 1L, Set.of(
                "order:status:budgeting",
                "order:status:budget-completed",
                "!order:status:pending-confirm"
        ));
        SalesOrder order = drawingBudgetOrder();
        when(salesOrderMapper.selectOne(any())).thenReturn(order);
        when(salesOrderMapper.selectByOrderIdForUpdate(ORDER_ID)).thenReturn(order);
        when(salesOrderMapper.update(any(), any())).thenReturn(1);

        SalesOrder advanced = service.advanceByFlowCode(flowScanCode());

        assertEquals(OrderStatusEnum.BUDGET_COMPLETED.getCode(), advanced.getStatus());
        ArgumentCaptor<SalesOrderStatusLog> logCaptor = ArgumentCaptor.forClass(SalesOrderStatusLog.class);
        verify(salesOrderStatusLogMapper).insert(logCaptor.capture());
        assertEquals(OrderStatusEnum.BUDGETING.getCode(), logCaptor.getValue().getOldStatus());
        assertEquals(OrderStatusEnum.BUDGET_COMPLETED.getCode(), logCaptor.getValue().getNewStatus());
        assertEquals("扫码推进订单至预算完成", logCaptor.getValue().getRemark());
        verify(productionOrderService).syncLinkedOrderStatus(
                ORDER_ID,
                OrderStatusEnum.BUDGET_COMPLETED.getCode(),
                "扫码推进订单至预算完成");
        verify(installationTaskSyncService).createOrSyncFromCompletedOrder(order);

        BusinessException terminal = assertThrows(
                BusinessException.class,
                () -> service.advanceByFlowCode(flowScanCode()));

        assertEquals(400, terminal.getCode());
        assertEquals("图纸预算已完成，当前状态无法继续流转", terminal.getMsg());
        verify(salesOrderMapper).update(any(), any());
    }

    @Test
    void drawingBudgetScanRequiresBudgetCompletedTargetPermission() {
        TenantPermissionContext.init(TENANT_CODE, 1L, Set.of(
                "order:status:budgeting",
                "!order:status:budget-completed"
        ));
        SalesOrder order = drawingBudgetOrder();
        when(salesOrderMapper.selectOne(any())).thenReturn(order);
        when(salesOrderMapper.selectByOrderIdForUpdate(ORDER_ID)).thenReturn(order);

        BusinessException forbidden = assertThrows(
                BusinessException.class,
                () -> service.advanceByFlowCode(flowScanCode()));

        assertEquals(403, forbidden.getCode());
        verify(salesOrderMapper, never()).update(any(), any());
    }

    @Test
    void standardCompletedScanKeepsGenericTerminalResult() {
        TenantPermissionContext.init(TENANT_CODE, 1L, Set.of("order:status:completed"));
        SalesOrder order = drawingBudgetOrder();
        order.setOrderCategory(OrderCategoryEnum.BULK.getCode());
        order.setStatus(OrderStatusEnum.COMPLETED.getCode());
        when(salesOrderMapper.selectOne(any())).thenReturn(order);

        BusinessException terminal = assertThrows(
                BusinessException.class,
                () -> service.advanceByFlowCode(flowScanCode()));

        assertEquals(400, terminal.getCode());
        assertEquals("当前状态无法继续流转", terminal.getMsg());
        verify(salesOrderMapper, never()).update(any(), any());
    }

    @Test
    void standardAndSpecialOrdersKeepTheExistingSalesSequence() {
        String standardNext = ReflectionTestUtils.invokeMethod(
                service,
                "resolveNextSalesStatus",
                OrderCategoryEnum.BULK.getCode(),
                OrderStatusEnum.PENDING_CONFIRM.getCode());
        String specialNext = ReflectionTestUtils.invokeMethod(
                service,
                "resolveNextSalesStatus",
                OrderCategoryEnum.SPECIAL_ORDER.getCode(),
                OrderStatusEnum.PENDING_CONFIRM.getCode());

        assertEquals(OrderStatusEnum.PENDING_PAY.getCode(), standardNext);
        assertEquals(OrderStatusEnum.PENDING_PAY.getCode(), specialNext);
    }

    @Test
    void budgetCompletedDrawingOrderCannotBeCancelled() {
        TenantPermissionContext.init(TENANT_CODE, 1L, Set.of(
                "order:status:budget-completed",
                "order:status:pending-cancel",
                "order:status:cancelled"
        ));
        SalesOrder order = drawingBudgetOrder();
        order.setStatus(OrderStatusEnum.BUDGET_COMPLETED.getCode());
        when(salesOrderMapper.selectByOrderIdForUpdate(ORDER_ID)).thenReturn(order);
        SalesOrderUpdateRequest request = new SalesOrderUpdateRequest();
        request.setStatus(OrderStatusEnum.CANCELLED.getCode());

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.updateStatusAndProcess(ORDER_ID, request));

        assertEquals(400, error.getCode());
        assertEquals("图纸预算已完成，不能取消、回退或继续流转", error.getMsg());
        verify(salesOrderMapper, never()).update(any(), any());
    }

    @Test
    void completedOrderCannotSubmitCancellation() {
        TenantPermissionContext.init(TENANT_CODE, 1L, Set.of(
                "order:status:completed",
                "order:status:pending-cancel"
        ));
        SalesOrder order = drawingBudgetOrder();
        order.setOrderCategory(OrderCategoryEnum.BULK.getCode());
        order.setStatus(OrderStatusEnum.COMPLETED.getCode());
        when(salesOrderMapper.selectByOrderIdForUpdate(ORDER_ID)).thenReturn(order);
        SalesOrderUpdateRequest request = new SalesOrderUpdateRequest();
        request.setStatus(OrderStatusEnum.CANCELLED.getCode());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateStatusAndProcess(ORDER_ID, request));

        assertEquals(400, error.getCode());
        verify(salesOrderMapper, never()).update(any(), any());
    }

    @Test
    void drawingBudgetCancelApprovalCannotMoveLegacyPendingCancelToCancelled() {
        SalesOrder order = drawingBudgetOrder();
        order.setStatus(OrderStatusEnum.PENDING_CANCEL.getCode());
        when(salesOrderMapper.selectByOrderIdForUpdate(ORDER_ID)).thenReturn(order);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.approvePendingCancelToCancelled(ORDER_ID, "approved"));

        assertEquals(400, error.getCode());
        assertEquals("图纸预算订单只能从预算中流转到预算完成", error.getMsg());
        verify(salesOrderMapper, never()).update(any(), any());
    }

    @Test
    void budgetCompletedDrawingOrderCannotSubmitRollback() {
        TenantPermissionContext.init(TENANT_CODE, 1L, Set.of("order:status:budget-completed"));
        SalesOrder order = drawingBudgetOrder();
        order.setStatus(OrderStatusEnum.BUDGET_COMPLETED.getCode());
        when(salesOrderMapper.selectOne(any())).thenReturn(order);
        SalesOrderUpdateRequest request = new SalesOrderUpdateRequest();
        request.setStatus(OrderStatusEnum.BUDGETING.getCode());
        request.setAuditorIds(List.of(2L));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.submitRollbackApproval(ORDER_ID, request));

        assertEquals(400, error.getCode());
        assertEquals("图纸预算已完成，不能提交回退审批", error.getMsg());
        verify(salesOrderStatusLogMapper, never()).insert(any());
        verify(approvalAuditorCandidateService, never()).replaceActiveCandidates(any(), any(), any(), any());
    }

    @Test
    void pendingShipScanSubmitsShipmentApprovalUsingSavedLogistics() {
        TenantPermissionContext.init(TENANT_CODE, 1L, Set.of(
                "order:status:pending-ship",
                "order:status:shipped"
        ));
        SalesOrder order = drawingBudgetOrder();
        order.setOrderCategory(OrderCategoryEnum.BULK.getCode());
        order.setStatus(OrderStatusEnum.PENDING_SHIP.getCode());
        order.setExpressCompany("SF");
        order.setExpressNo("SF123");
        when(salesOrderMapper.selectOne(any())).thenReturn(order);
        when(salesOrderMapper.selectByOrderIdForUpdate(ORDER_ID)).thenReturn(order);
        when(salesOrderMapper.update(any(), any())).thenReturn(1);
        when(approvalAuditorCandidateService.findPendingAuditorIds(any(), any(), any())).thenReturn(List.of());
        when(approvalDefaultAuditorService.resolveAuditorIds(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(List.of(2L));
        when(userMapper.selectActiveApproverIdsByPermission(any(), any())).thenReturn(List.of(2L));

        SalesOrder advanced = service.advanceByFlowCode(flowScanCode());

        assertEquals(OrderStatusEnum.PENDING_SHIP.getCode(), advanced.getStatus());
        verify(approvalAuditorCandidateService).createActiveCandidates(
                TENANT_CODE, "ORDER", "sales:" + ORDER_ID, List.of(2L));
        ArgumentCaptor<SalesOrderStatusLog> logCaptor = ArgumentCaptor.forClass(SalesOrderStatusLog.class);
        verify(salesOrderStatusLogMapper).insert(logCaptor.capture());
        assertEquals("shipment_approval_pending", logCaptor.getValue().getOperateType());
        assertEquals(OrderStatusEnum.SHIPPED.getCode(), logCaptor.getValue().getNewStatus());
    }

    private SalesOrder drawingBudgetOrder() {
        SalesOrder order = new SalesOrder();
        order.setOrderId(ORDER_ID);
        order.setTenantCode(TENANT_CODE);
        order.setOrderCategory(OrderCategoryEnum.DRAWING_BUDGET.getCode());
        order.setStatus(OrderStatusEnum.BUDGETING.getCode());
        order.setCreator("1");
        return order;
    }

    private String flowScanCode() {
        String flowCode = OrderFlowCodeUtil.generateFlowCode(FLOW_SECRET, TENANT_CODE, "sales", ORDER_ID);
        return OrderFlowCodeUtil.buildScanCode("sales", flowCode, ORDER_ID);
    }
}

package my.hive_back.order;

import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive.common.order.OrderFlowCodeUtil;
import my.hive_back.module.installation.service.InstallationTaskSyncService;
import my.hive_back.module.order.OrderCategoryEnum;
import my.hive_back.module.order.OrderStatusEnum;
import my.hive_back.module.order.mapper.SalesOrderMapper;
import my.hive_back.module.order.mapper.SalesOrderStatusLogMapper;
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

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
        when(salesOrderMapper.selectOne(any())).thenReturn(drawingBudgetOrder());

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

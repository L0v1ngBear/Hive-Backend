package my.hive_back.order;

import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive_back.module.approval.service.ApprovalAuditorCandidateService;
import my.hive_back.module.approval.service.ApprovalDefaultAuditorService;
import my.hive_back.module.installation.service.InstallationTaskSyncService;
import my.hive_back.module.order.OrderStatusEnum;
import my.hive_back.module.order.mapper.SalesOrderDetailMapper;
import my.hive_back.module.order.mapper.SalesOrderMapper;
import my.hive_back.module.order.mapper.SalesOrderStatusLogMapper;
import my.hive_back.module.order.model.dto.SalesOrderUpdateRequest;
import my.hive_back.module.order.model.dto.UnifiedOrderUpdateRequest;
import my.hive_back.module.order.model.entity.SalesOrder;
import my.hive_back.module.order.model.entity.SalesOrderDetail;
import my.hive_back.module.order.service.ProductionOrderService;
import my.hive_back.module.order.service.SalesOrderService;
import my.hive_back.module.user.mapper.UserMapper;
import my.hive_back.module.wechat.service.WechatSubscribeNotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesOrderShipmentApprovalTest {

    private static final String TENANT_CODE = "TENANT-TEST";
    private static final String ORDER_ID = "SO-SHIP-001";

    @Mock
    private SalesOrderMapper salesOrderMapper;

    @Mock
    private SalesOrderStatusLogMapper salesOrderStatusLogMapper;

    @Mock
    private SalesOrderDetailMapper salesOrderDetailMapper;

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
        TenantPermissionContext.init(TENANT_CODE, 1L, Set.of(
                "order:status:pending-ship",
                "order:status:shipped"
        ));
        service = new SalesOrderService();
        ReflectionTestUtils.setField(service, "salesOrderMapper", salesOrderMapper);
        ReflectionTestUtils.setField(service, "salesOrderStatusLogMapper", salesOrderStatusLogMapper);
        ReflectionTestUtils.setField(service, "salesOrderDetailMapper", salesOrderDetailMapper);
        ReflectionTestUtils.setField(service, "productionOrderService", productionOrderService);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "wechatSubscribeNotificationService", wechatSubscribeNotificationService);
        ReflectionTestUtils.setField(service, "installationTaskSyncService", installationTaskSyncService);
        ReflectionTestUtils.setField(service, "approvalAuditorCandidateService", approvalAuditorCandidateService);
        ReflectionTestUtils.setField(service, "approvalDefaultAuditorService", approvalDefaultAuditorService);
    }

    @AfterEach
    void clearContext() {
        TenantPermissionContext.clear();
    }

    @Test
    void shipmentUpdateRequestExposesInformationChannel() throws NoSuchFieldException {
        assertEquals(String.class, SalesOrderUpdateRequest.class
                .getDeclaredField("informationChannel")
                .getType());
    }

    @Test
    void sameStatusEditPersistsFieldsAndItemsWithoutStatusSideEffects() {
        SalesOrder order = pendingShipOrder();
        when(salesOrderMapper.selectByOrderIdForUpdate(ORDER_ID)).thenReturn(order);
        when(salesOrderMapper.update(any(), any())).thenReturn(1);
        UnifiedOrderUpdateRequest request = new UnifiedOrderUpdateRequest();
        request.setCustomerName("Acme");
        request.setProjectName("Tower A");
        request.setBrandName("Hive");
        request.setOrderCategory("bulk");
        request.setInformationChannel("partner");
        request.setRemark("saved only");
        UnifiedOrderUpdateRequest.ExpressInfo expressInfo = new UnifiedOrderUpdateRequest.ExpressInfo();
        expressInfo.setExpressCompany("SF");
        expressInfo.setExpressNo("SF-002");
        request.setExpressInfo(expressInfo);
        UnifiedOrderUpdateRequest.OrderItemDTO item = new UnifiedOrderUpdateRequest.OrderItemDTO();
        item.setModelCode("M-1");
        item.setQuantity(java.math.BigDecimal.valueOf(2));
        item.setWeight("12kg");
        item.setSpec("3.5");
        request.setItems(List.of(item));

        SalesOrder result = service.updateEditableContent(ORDER_ID, request);

        assertEquals(OrderStatusEnum.PENDING_SHIP.getCode(), result.getStatus());
        assertEquals("Acme", result.getCustomerName());
        assertEquals("partner", result.getInformationChannel());
        assertEquals("SF-002", result.getExpressNo());
        ArgumentCaptor<SalesOrderDetail> itemCaptor = ArgumentCaptor.forClass(SalesOrderDetail.class);
        verify(salesOrderDetailMapper).insert(itemCaptor.capture());
        assertEquals("M-1", itemCaptor.getValue().getModelCode());
        assertEquals("3.5", itemCaptor.getValue().getSpec());
        verify(salesOrderDetailMapper).delete(any());
        verify(salesOrderStatusLogMapper, never()).insert(any());
        verify(productionOrderService, never()).syncLinkedOrderStatus(any(), any(), any());
        verify(installationTaskSyncService, never()).createOrSyncFromCompletedOrder(any());
    }

    @Test
    void submittingShipmentApprovalPersistsFulfillmentAndKeepsPendingShip() {
        SalesOrder order = pendingShipOrder();
        when(salesOrderMapper.selectByOrderIdForUpdate(ORDER_ID)).thenReturn(order);
        when(salesOrderMapper.update(any(), any())).thenReturn(1);
        when(approvalAuditorCandidateService.findPendingAuditorIds(TENANT_CODE, "ORDER", "sales:" + ORDER_ID))
                .thenReturn(List.of());
        when(approvalDefaultAuditorService.resolveAuditorIds(
                eq(TENANT_CODE), eq("ORDER"), eq(1L), eq(null), eq(null), any(), eq(false)))
                .thenReturn(List.of(2L));
        when(userMapper.selectActiveApproverIdsByPermission(any(), any())).thenReturn(List.of(2L));

        SalesOrder result = service.updateStatusAndProcess(ORDER_ID, shipmentRequest());

        assertEquals(OrderStatusEnum.PENDING_SHIP.getCode(), result.getStatus());
        assertEquals("SF", result.getExpressCompany());
        assertEquals("SF-001", result.getExpressNo());
        assertEquals("wechat", result.getInformationChannel());
        assertEquals("Ready for shipment approval", result.getRemark());
        verify(approvalAuditorCandidateService).createActiveCandidates(
                eq(TENANT_CODE), eq("ORDER"), eq("sales:" + ORDER_ID), eq(List.of(2L)));
        verify(salesOrderStatusLogMapper).insert(any());
        verify(productionOrderService, never()).syncLinkedOrderStatus(any(), any(), any());
    }

    @Test
    void missingLogisticsDoesNotPersistOrCreateShipmentApproval() {
        SalesOrder order = pendingShipOrder();
        when(salesOrderMapper.selectByOrderIdForUpdate(ORDER_ID)).thenReturn(order);
        SalesOrderUpdateRequest request = shipmentRequest();
        request.setExpressInfo(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateStatusAndProcess(ORDER_ID, request));

        assertEquals(400, exception.getCode());
        assertEquals(OrderStatusEnum.PENDING_SHIP.getCode(), order.getStatus());
        verify(salesOrderMapper, never()).update(any(), any());
        verify(approvalAuditorCandidateService, never()).replaceActiveCandidates(any(), any(), any(), any());
        verify(salesOrderStatusLogMapper, never()).insert(any());
    }

    @Test
    void existingShipmentApprovalCannotBeSubmittedTwice() {
        SalesOrder order = pendingShipOrder();
        when(salesOrderMapper.selectByOrderIdForUpdate(ORDER_ID)).thenReturn(order);
        when(approvalAuditorCandidateService.findPendingAuditorIds(TENANT_CODE, "ORDER", "sales:" + ORDER_ID))
                .thenReturn(List.of(2L));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateStatusAndProcess(ORDER_ID, shipmentRequest()));

        assertEquals(400, exception.getCode());
        assertTrue(exception.getMsg().contains("待处理审批"));
        verify(salesOrderMapper, never()).update(any(), any());
        verify(approvalAuditorCandidateService, never()).replaceActiveCandidates(any(), any(), any(), any());
    }

    @Test
    void duplicateShipmentSubmissionNeverReplacesCandidatesOrDuplicatesLog() {
        SalesOrder order = pendingShipOrder();
        AtomicBoolean activeApproval = new AtomicBoolean(false);
        when(salesOrderMapper.selectByOrderIdForUpdate(ORDER_ID)).thenReturn(order);
        when(salesOrderMapper.update(any(), any())).thenAnswer(invocation -> {
            activeApproval.set(true);
            return 1;
        });
        when(approvalAuditorCandidateService.findPendingAuditorIds(
                TENANT_CODE, "ORDER", "sales:" + ORDER_ID))
                .thenAnswer(invocation -> activeApproval.get() ? List.of(2L) : List.of());
        when(approvalDefaultAuditorService.resolveAuditorIds(
                eq(TENANT_CODE), eq("ORDER"), eq(1L), eq(null), eq(null), any(), eq(false)))
                .thenReturn(List.of(2L));
        when(userMapper.selectActiveApproverIdsByPermission(any(), any())).thenReturn(List.of(2L));

        service.updateStatusAndProcess(ORDER_ID, shipmentRequest());
        assertThrows(BusinessException.class,
                () -> service.updateStatusAndProcess(ORDER_ID, shipmentRequest()));

        verify(salesOrderMapper).update(any(), any());
        verify(salesOrderStatusLogMapper).insert(any());
        verify(approvalAuditorCandidateService, never()).replaceActiveCandidates(any(), any(), any(), any());
    }

    @Test
    void approvingShipmentAfterAllAuditorsPassChangesStatusToShipped() {
        SalesOrder order = pendingShipOrder();
        order.setExpressCompany("SF");
        order.setExpressNo("SF-001");
        when(salesOrderMapper.selectByOrderIdForUpdate(ORDER_ID)).thenReturn(order);
        when(salesOrderStatusLogMapper.selectOne(any())).thenReturn(shipmentApprovalLog());
        when(salesOrderMapper.update(any(), any())).thenReturn(1);

        SalesOrder result = service.approveShipment(ORDER_ID, "approved");

        assertEquals(OrderStatusEnum.SHIPPED.getCode(), result.getStatus());
        verify(productionOrderService).syncLinkedOrderStatus(ORDER_ID, OrderStatusEnum.SHIPPED.getCode(), "approved");
    }

    private SalesOrder pendingShipOrder() {
        SalesOrder order = new SalesOrder();
        order.setOrderId(ORDER_ID);
        order.setTenantCode(TENANT_CODE);
        order.setStatus(OrderStatusEnum.PENDING_SHIP.getCode());
        order.setCreator("1");
        return order;
    }

    private SalesOrderUpdateRequest shipmentRequest() {
        SalesOrderUpdateRequest request = new SalesOrderUpdateRequest();
        request.setStatus(OrderStatusEnum.SHIPPED.getCode());
        request.setRemark("Ready for shipment approval");
        request.setInformationChannel("wechat");
        SalesOrderUpdateRequest.ExpressInfo expressInfo = new SalesOrderUpdateRequest.ExpressInfo();
        expressInfo.setExpressCompany("SF");
        expressInfo.setExpressNo("SF-001");
        request.setExpressInfo(expressInfo);
        assertNotNull(request.getExpressInfo());
        return request;
    }

    private my.hive_back.module.order.model.entity.SalesOrderStatusLog shipmentApprovalLog() {
        my.hive_back.module.order.model.entity.SalesOrderStatusLog log = new my.hive_back.module.order.model.entity.SalesOrderStatusLog();
        log.setOrderId(ORDER_ID);
        log.setOldStatus(OrderStatusEnum.PENDING_SHIP.getCode());
        log.setNewStatus(OrderStatusEnum.SHIPPED.getCode());
        return log;
    }
}

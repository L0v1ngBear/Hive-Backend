package my.hive_back.order;

import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive_back.common.utils.CodeGeneratorUtil;
import my.hive_back.module.approval.service.ApprovalAuditorCandidateService;
import my.hive_back.module.approval.service.ApprovalDefaultAuditorService;
import my.hive_back.module.installation.service.InstallationTaskSyncService;
import my.hive_back.module.order.OrderCategoryEnum;
import my.hive_back.module.order.OrderStatusEnum;
import my.hive_back.module.order.mapper.SalesOrderDetailMapper;
import my.hive_back.module.order.mapper.SalesOrderMapper;
import my.hive_back.module.order.mapper.SalesOrderStatusLogMapper;
import my.hive_back.module.order.model.dto.SalesOrderAddRequest;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.Invocation;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesOrderEditingRulesTest {

    private static final String ORDER_ID = "SO-EDIT-RULES-001";

    @Mock private SalesOrderMapper salesOrderMapper;
    @Mock private SalesOrderDetailMapper salesOrderDetailMapper;
    @Mock private SalesOrderStatusLogMapper salesOrderStatusLogMapper;
    @Mock private ProductionOrderService productionOrderService;
    @Mock private UserMapper userMapper;
    @Mock private WechatSubscribeNotificationService notificationService;
    @Mock private InstallationTaskSyncService installationTaskSyncService;
    @Mock private ApprovalAuditorCandidateService candidateService;
    @Mock private ApprovalDefaultAuditorService defaultAuditorService;
    @Mock private CodeGeneratorUtil codeGeneratorUtil;

    private SalesOrderService service;

    @BeforeEach
    void setUp() {
        TenantPermissionContext.init("TENANT-TEST", 1L, Set.of(
                "order:status:pending-confirm",
                "order:status:budgeting",
                "order:status:budget-completed",
                "order:status:completed",
                "order:status:cancelled"
        ));
        service = new SalesOrderService();
        ReflectionTestUtils.setField(service, "salesOrderMapper", salesOrderMapper);
        ReflectionTestUtils.setField(service, "salesOrderDetailMapper", salesOrderDetailMapper);
        ReflectionTestUtils.setField(service, "salesOrderStatusLogMapper", salesOrderStatusLogMapper);
        ReflectionTestUtils.setField(service, "productionOrderService", productionOrderService);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "wechatSubscribeNotificationService", notificationService);
        ReflectionTestUtils.setField(service, "installationTaskSyncService", installationTaskSyncService);
        ReflectionTestUtils.setField(service, "approvalAuditorCandidateService", candidateService);
        ReflectionTestUtils.setField(service, "approvalDefaultAuditorService", defaultAuditorService);
        ReflectionTestUtils.setField(service, "codeGeneratorUtil", codeGeneratorUtil);
    }

    @AfterEach
    void clearContext() {
        TenantPermissionContext.clear();
    }

    @Test
    void specialOrderCannotChangeCategoryToBypassApproval() {
        SalesOrder order = order(OrderCategoryEnum.SPECIAL_ORDER.getCode(), OrderStatusEnum.PENDING_CONFIRM.getCode());
        when(salesOrderMapper.selectByOrderIdForUpdate(ORDER_ID)).thenReturn(order);
        UnifiedOrderUpdateRequest request = editableRequest();
        request.setOrderCategory(OrderCategoryEnum.BULK.getCode());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateEditableContent(ORDER_ID, request));

        assertEquals(400, error.getCode());
        verify(salesOrderMapper, never()).update(any(), any());
    }

    @Test
    void drawingBudgetCannotChangeCategoryWhileBudgeting() {
        SalesOrder order = order(OrderCategoryEnum.DRAWING_BUDGET.getCode(), OrderStatusEnum.BUDGETING.getCode());
        when(salesOrderMapper.selectByOrderIdForUpdate(ORDER_ID)).thenReturn(order);
        UnifiedOrderUpdateRequest request = editableRequest();
        request.setOrderCategory(OrderCategoryEnum.BULK.getCode());
        request.setInformationChannel(null);

        assertThrows(BusinessException.class, () -> service.updateEditableContent(ORDER_ID, request));

        verify(salesOrderMapper, never()).update(any(), any());
    }

    @Test
    void terminalOrdersRejectEveryContentEdit() {
        when(salesOrderMapper.selectByOrderIdForUpdate(ORDER_ID)).thenReturn(
                order(OrderCategoryEnum.DRAWING_BUDGET.getCode(), OrderStatusEnum.BUDGET_COMPLETED.getCode()),
                order(OrderCategoryEnum.BULK.getCode(), OrderStatusEnum.COMPLETED.getCode()),
                order(OrderCategoryEnum.BULK.getCode(), OrderStatusEnum.CANCELLED.getCode()));

        assertThrows(BusinessException.class, () -> service.updateEditableContent(ORDER_ID, editableRequest()));
        assertThrows(BusinessException.class, () -> service.updateEditableContent(ORDER_ID, editableRequest()));
        assertThrows(BusinessException.class, () -> service.updateEditableContent(ORDER_ID, editableRequest()));

        verify(salesOrderMapper, never()).update(any(), any());
    }

    @Test
    void ordinaryOrderCannotClearInformationChannel() {
        SalesOrder order = order(OrderCategoryEnum.BULK.getCode(), OrderStatusEnum.PENDING_CONFIRM.getCode());
        when(salesOrderMapper.selectByOrderIdForUpdate(ORDER_ID)).thenReturn(order);
        UnifiedOrderUpdateRequest request = editableRequest();
        request.setInformationChannel("   ");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateEditableContent(ORDER_ID, request));

        assertEquals(400, error.getCode());
        verify(salesOrderMapper, never()).update(any(), any());
    }

    @Test
    void editSynchronizesSharedFieldsAndItemsToExistingProductionOrders() {
        SalesOrder order = order(OrderCategoryEnum.BULK.getCode(), OrderStatusEnum.PENDING_CONFIRM.getCode());
        when(salesOrderMapper.selectByOrderIdForUpdate(ORDER_ID)).thenReturn(order);
        when(salesOrderMapper.update(any(), any())).thenReturn(1);
        UnifiedOrderUpdateRequest request = editableRequest();
        request.setCustomerName("New customer");
        request.setProjectName("New project");
        request.setBrandName("New brand");
        request.setItems(List.of(item("MODEL-2", 4, "0140.50")));

        service.updateEditableContent(ORDER_ID, request);

        Invocation syncInvocation = mockingDetails(productionOrderService).getInvocations().stream()
                .filter(invocation -> "syncLinkedEditableContent".equals(invocation.getMethod().getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("editing must synchronize linked production orders"));
        SalesOrder synchronizedOrder = (SalesOrder) syncInvocation.getArgument(0);
        assertEquals("New customer", synchronizedOrder.getCustomerName());
        assertEquals("partner", synchronizedOrder.getInformationChannel());
        assertEquals("MODEL-2", ((List<?>) syncInvocation.getArgument(1)).stream()
                .map(UnifiedOrderUpdateRequest.OrderItemDTO.class::cast)
                .findFirst().orElseThrow().getModelCode());
        verify(salesOrderStatusLogMapper, never()).insert(any());
    }

    @Test
    void createPreservesTrimmedTextItemSpec() {
        when(codeGeneratorUtil.generateSalesOrderCode()).thenReturn(ORDER_ID);
        SalesOrderAddRequest request = new SalesOrderAddRequest();
        request.setCustomerName("Customer");
        request.setProjectName("Project");
        request.setOrderCategory(OrderCategoryEnum.BULK.getCode());
        request.setInformationChannel("partner");
        request.setCreateProductionOrder(0);
        SalesOrderAddRequest.OrderItemDTO item = request.new OrderItemDTO();
        item.setModelCode("MODEL-1");
        item.setQuantity(BigDecimal.ONE);
        item.setSpec(" 2x3 ");
        request.setItems(List.of(item));

        service.addSalesOrder(request);

        ArgumentCaptor<SalesOrderDetail> detailCaptor = ArgumentCaptor.forClass(SalesOrderDetail.class);
        verify(salesOrderDetailMapper).insert(detailCaptor.capture());
        assertEquals("2x3", detailCaptor.getValue().getSpec());
    }

    private SalesOrder order(String category, String status) {
        SalesOrder order = new SalesOrder();
        order.setOrderId(ORDER_ID);
        order.setTenantCode("TENANT-TEST");
        order.setOrderCategory(category);
        order.setStatus(status);
        order.setInformationChannel("existing");
        order.setCreator("1");
        return order;
    }

    private UnifiedOrderUpdateRequest editableRequest() {
        UnifiedOrderUpdateRequest request = new UnifiedOrderUpdateRequest();
        request.setCustomerName("Customer");
        request.setInformationChannel("partner");
        return request;
    }

    private UnifiedOrderUpdateRequest.OrderItemDTO item(String modelCode, int quantity, String spec) {
        UnifiedOrderUpdateRequest.OrderItemDTO item = new UnifiedOrderUpdateRequest.OrderItemDTO();
        item.setModelCode(modelCode);
        item.setQuantity(BigDecimal.valueOf(quantity));
        item.setSpec(spec);
        return item;
    }
}

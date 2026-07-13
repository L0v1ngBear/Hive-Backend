package my.hive_back.order;

import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive_back.module.order.OrderCategoryEnum;
import my.hive_back.module.order.OrderStatusEnum;
import my.hive_back.module.order.mapper.ProductionOrderMapper;
import my.hive_back.module.order.mapper.SalesOrderMapper;
import my.hive_back.module.order.model.dto.ProductionOrderUpdateRequest;
import my.hive_back.module.order.model.dto.SalesOrderUpdateRequest;
import my.hive_back.module.order.model.entity.ProductionOrder;
import my.hive_back.module.order.model.entity.SalesOrder;
import my.hive_back.module.order.service.ProductionOrderService;
import my.hive_back.module.order.service.SalesOrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPermissionScopeTest {

    @Mock
    private SalesOrderMapper salesOrderMapper;

    @Mock
    private ProductionOrderMapper productionOrderMapper;

    @AfterEach
    void clearContext() {
        TenantPermissionContext.clear();
    }

    @Test
    void salesOrderScopeAppliesPersonalDenyOverStatusWildcard() {
        TenantPermissionContext.init("TENANT-TEST", 1L, Set.of(
                "order:status:*",
                "!order:status:producing"
        ));

        Set<String> permitted = ReflectionTestUtils.invokeMethod(
                new SalesOrderService(),
                "permittedOrderStatuses",
                List.of("producing", "pending_ship"));

        assertEquals(Set.of("pending_ship"), permitted);
    }

    @Test
    void productionOrderScopeAppliesPersonalDenyOverDirectRoleGrant() {
        TenantPermissionContext.init("TENANT-TEST", 1L, Set.of(
                "order:status:pending-material",
                "order:status:producing",
                "!order:status:producing"
        ));

        Set<String> permitted = ReflectionTestUtils.invokeMethod(
                new ProductionOrderService(),
                "permittedOrderStatuses",
                List.of("pending_material", "producing", "pending_ship"));

        assertEquals(Set.of("pending_material"), permitted);
    }

    @Test
    void salesOrderStatusUpdateAppliesPersonalDenyOverStatusWildcard() {
        TenantPermissionContext.init("TENANT-TEST", 1L, Set.of(
                "order:status:*",
                "!order:status:producing"
        ));

        assertThrows(BusinessException.class, () -> ReflectionTestUtils.invokeMethod(
                new SalesOrderService(),
                "assertOrderStatusPermission",
                "producing"));
    }

    @Test
    void productionOrderStatusUpdateAppliesPersonalDenyOverStatusWildcard() {
        TenantPermissionContext.init("TENANT-TEST", 1L, Set.of(
                "order:status:*",
                "!order:status:producing"
        ));

        assertThrows(BusinessException.class, () -> ReflectionTestUtils.invokeMethod(
                new ProductionOrderService(),
                "assertOrderStatusPermission",
                "producing"));
    }

    @Test
    void salesOrderTransitionRequiresPermissionForTheTargetStage() {
        TenantPermissionContext.init("TENANT-TEST", 1L, Set.of(
                "order:status:producing",
                "!order:status:pending-ship"
        ));
        SalesOrder order = new SalesOrder();
        order.setOrderId("SO-001");
        order.setOrderCategory(OrderCategoryEnum.BULK.getCode());
        order.setStatus(OrderStatusEnum.PRODUCING.getCode());
        when(salesOrderMapper.selectByOrderIdForUpdate(order.getOrderId())).thenReturn(order);
        SalesOrderService service = new SalesOrderService();
        ReflectionTestUtils.setField(service, "salesOrderMapper", salesOrderMapper);
        SalesOrderUpdateRequest request = new SalesOrderUpdateRequest();
        request.setStatus(OrderStatusEnum.PENDING_SHIP.getCode());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateStatusAndProcess(order.getOrderId(), request));

        assertEquals(403, error.getCode());
    }

    @Test
    void productionOrderTransitionRequiresPermissionForTheTargetStage() {
        TenantPermissionContext.init("TENANT-TEST", 1L, Set.of(
                "order:status:pending-material",
                "!order:status:producing"
        ));
        ProductionOrder order = new ProductionOrder();
        order.setOrderId("PO-001");
        order.setStatus(OrderStatusEnum.PENDING_MATERIAL.getCode());
        when(productionOrderMapper.selectOne(any())).thenReturn(order);
        ProductionOrderService service = new ProductionOrderService();
        ReflectionTestUtils.setField(service, "productionOrderMapper", productionOrderMapper);
        ProductionOrderUpdateRequest request = new ProductionOrderUpdateRequest();
        request.setStatus(OrderStatusEnum.PRODUCING.getCode());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateStatusAndProcess(order.getOrderId(), request));

        assertEquals(403, error.getCode());
    }
}

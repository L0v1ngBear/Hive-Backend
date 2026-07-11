package my.hive_back.order;

import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive_back.module.order.service.ProductionOrderService;
import my.hive_back.module.order.service.SalesOrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderPermissionScopeTest {

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
}

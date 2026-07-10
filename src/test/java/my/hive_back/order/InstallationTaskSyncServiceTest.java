package my.hive_back.order;

import my.hive_back.module.installation.mapper.InstallationTaskMapper;
import my.hive_back.module.installation.model.entity.InstallationTask;
import my.hive_back.module.installation.service.InstallationTaskSyncService;
import my.hive_back.module.order.OrderStatusEnum;
import my.hive_back.module.order.model.entity.SalesOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InstallationTaskSyncServiceTest {

    @Mock
    private InstallationTaskMapper installationTaskMapper;

    private InstallationTaskSyncService service;

    @BeforeEach
    void setUp() {
        service = new InstallationTaskSyncService();
        ReflectionTestUtils.setField(service, "installationTaskMapper", installationTaskMapper);
    }

    @Test
    void completedOrderIsMappedToAtomicInstallationTaskUpsert() {
        SalesOrder order = new SalesOrder();
        order.setTenantCode("TENANT-1");
        order.setOrderId("SO-001");
        order.setStatus(OrderStatusEnum.COMPLETED.getCode());
        order.setCustomerName("测试客户");
        order.setCustomerPhone("13800000000");
        order.setExpressCompany("顺丰");
        order.setExpressNo("SF001");
        order.setAttachmentName("contract.pdf");

        service.createOrSyncFromCompletedOrder(order);

        ArgumentCaptor<InstallationTask> captor = ArgumentCaptor.forClass(InstallationTask.class);
        verify(installationTaskMapper).upsertFromCompletedOrder(captor.capture());
        InstallationTask task = captor.getValue();
        assertEquals("TENANT-1", task.getTenantCode());
        assertEquals("SO-001", task.getOrderId());
        assertEquals("production_completed", task.getInstallationStatus());
        assertEquals("13800000000", task.getCustomerPhone());
        assertEquals("SF001", task.getExpressNo());
        assertEquals("contract.pdf", task.getOrderAttachmentName());
        assertNotNull(task.getOrderCompletedTime());
    }

    @Test
    void unfinishedOrderDoesNotCreateInstallationTask() {
        SalesOrder order = new SalesOrder();
        order.setStatus(OrderStatusEnum.SHIPPED.getCode());

        service.createOrSyncFromCompletedOrder(order);

        verify(installationTaskMapper, never()).upsertFromCompletedOrder(org.mockito.ArgumentMatchers.any());
    }
}

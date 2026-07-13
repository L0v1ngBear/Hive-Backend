package my.hive_back.order;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.dto.PageResult;
import my.hive.common.order.OrderFlowCodeUtil;
import my.hive_back.module.order.model.dto.BaseOrderListRequest;
import my.hive_back.module.order.model.dto.SalesOrderListRequest;
import my.hive_back.module.order.model.dto.UnifiedOrderUpdateRequest;
import my.hive_back.module.order.model.entity.ProductionOrder;
import my.hive_back.module.order.model.entity.SalesOrder;
import my.hive_back.module.order.model.vo.ProductionOrderVO;
import my.hive_back.module.order.model.vo.SalesOrderVO;
import my.hive_back.module.order.service.OrderService;
import my.hive_back.module.order.service.ProductionOrderService;
import my.hive_back.module.order.service.SalesOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private SalesOrderService salesOrderService;

    @Mock
    private ProductionOrderService productionOrderService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService();
        ReflectionTestUtils.setField(orderService, "salesOrderService", salesOrderService);
        ReflectionTestUtils.setField(orderService, "productionOrderService", productionOrderService);
    }

    @org.junit.jupiter.api.AfterEach
    void clearTenantContext() {
        TenantPermissionContext.clear();
    }

    @Test
    void detailReturnsRawTenantSignedFlowCodeWithoutPrintingTask() {
        TenantPermissionContext.init("TENANT-DETAIL", 7L, Set.of("order:status:pending-ship"));
        ReflectionTestUtils.setField(orderService, "orderFlowCodeSecret", "detail-flow-secret");
        SalesOrderVO order = salesOrder("SO-DETAIL-001", "pending_ship");
        when(salesOrderService.getByIdandTenantId("SO-DETAIL-001")).thenReturn(order);
        when(productionOrderService.listBySalesOrderIds(List.of("SO-DETAIL-001"))).thenReturn(List.of());

        Map<String, Object> detail = orderService.detail("SO-DETAIL-001");

        String flowCode = (String) detail.get("flowCode");
        String flowScanCode = (String) detail.get("flowScanCode");
        OrderFlowCodeUtil.Parsed parsed = OrderFlowCodeUtil.parse(flowScanCode);
        assertEquals(43, flowCode.length());
        assertFalse(flowCode.contains(":"));
        assertEquals(flowCode, parsed.flowCode());
        assertEquals("sales", parsed.orderType());
        assertEquals("SO-DETAIL-001", parsed.orderId());
        assertTrue(OrderFlowCodeUtil.matches(
                "detail-flow-secret", "TENANT-DETAIL", parsed));
        assertFalse(flowCode.startsWith("{"));
        verify(salesOrderService).getByIdandTenantId("SO-DETAIL-001");
    }

    @Test
    void sameStatusUpdatePersistsEditableContentWithoutTransition() {
        TenantPermissionContext.init("TENANT-EDIT", 7L, Set.of("order:status:pending-ship"));
        SalesOrderVO order = salesOrder("SO-EDIT-001", "pending_ship");
        when(salesOrderService.getByIdandTenantId("SO-EDIT-001")).thenReturn(order);
        when(productionOrderService.listBySalesOrderIds(List.of("SO-EDIT-001"))).thenReturn(List.of());
        UnifiedOrderUpdateRequest request = new UnifiedOrderUpdateRequest();
        request.setStatus("pending_ship");
        request.setInformationChannel("WeChat referral");
        request.setRemark("saved before shipment approval");
        UnifiedOrderUpdateRequest.ExpressInfo expressInfo = new UnifiedOrderUpdateRequest.ExpressInfo();
        expressInfo.setExpressCompany("SF");
        expressInfo.setExpressNo("SF123");
        request.setExpressInfo(expressInfo);

        Map<String, Object> result = orderService.update("SO-EDIT-001", request);

        assertEquals("SO-EDIT-001", result.get("orderId"));
        verify(salesOrderService).updateEditableContent("SO-EDIT-001", request);
        verify(salesOrderService, never()).updateStatusAndProcess(any(), any());
    }

    @Test
    void pageReturnsOneCanonicalRowWhenOrderHasMultipleFulfillmentRows() {
        SalesOrderVO first = salesOrder("SO-001", "producing");
        SalesOrderVO second = salesOrder("SO-002", "pending_pay");
        Page<SalesOrderVO> canonicalPage = new Page<>(2, 2, 5);
        canonicalPage.setRecords(List.of(first, second));
        when(salesOrderService.selectSalesOrder(any(SalesOrderListRequest.class))).thenReturn(canonicalPage);

        ProductionOrder firstFulfillment = fulfillment("PO-001", "SO-001", 2);
        ProductionOrder secondFulfillment = fulfillment("PO-002", "SO-001", 1);
        when(productionOrderService.listBySalesOrderIds(List.of("SO-001", "SO-002")))
                .thenReturn(List.of(firstFulfillment, secondFulfillment));
        ProductionOrderVO fulfillmentView = new ProductionOrderVO();
        fulfillmentView.setProcess(1);
        fulfillmentView.setCurrentProcessText("尺寸裁剪");
        when(productionOrderService.toVO(secondFulfillment)).thenReturn(fulfillmentView);

        BaseOrderListRequest request = new BaseOrderListRequest();
        request.setPageNum(2);
        request.setPageSize(2);
        PageResult<Map<String, Object>> result = orderService.page(request);

        assertEquals(2L, result.getCurrent());
        assertEquals(2L, result.getSize());
        assertEquals(5L, result.getTotal());
        assertEquals(2, result.getData().size());
        assertEquals("SO-001", result.getData().get(0).get("orderId"));
        assertTrue((Boolean) result.getData().get(0).get("fulfillmentTracked"));
        assertEquals(2, result.getData().get(0).get("fulfillmentRecordCount"));
        assertEquals(1, result.getData().get(0).get("process"));
        assertFalse(result.getData().get(0).containsKey("orderType"));
        assertFalse((Boolean) result.getData().get(1).get("fulfillmentTracked"));
        assertEquals(0, result.getData().get(1).get("fulfillmentRecordCount"));
        assertNull(result.getData().get(1).get("process"));

        ArgumentCaptor<SalesOrderListRequest> requestCaptor = ArgumentCaptor.forClass(SalesOrderListRequest.class);
        verify(salesOrderService).selectSalesOrder(requestCaptor.capture());
        assertEquals(2, requestCaptor.getValue().getPageNum());
        assertEquals(2, requestCaptor.getValue().getPageSize());
    }

    @Test
    void canonicalSalesOrderVoRetainsFieldsUsedByMiniProgram() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 10, 10, 30);
        SalesOrder entity = new SalesOrder();
        entity.setCustomerPhone("13800000000");
        entity.setGoodsDesc("窗帘 3 套");
        entity.setTotalQuantity(3);
        entity.setRemark("加急");
        entity.setAttachmentName("order.pdf");
        entity.setAttachmentUrl("/uploads/order.pdf");
        entity.setAttachmentSize(1024L);
        entity.setCreateTime(now);
        entity.setUpdateTime(now.plusHours(1));

        SalesOrderVO vo = new SalesOrderVO();
        BeanUtils.copyProperties(entity, vo);

        assertEquals(entity.getCustomerPhone(), vo.getCustomerPhone());
        assertEquals(entity.getGoodsDesc(), vo.getGoodsDesc());
        assertEquals(entity.getTotalQuantity(), vo.getTotalQuantity());
        assertEquals(entity.getRemark(), vo.getRemark());
        assertEquals(entity.getAttachmentName(), vo.getAttachmentName());
        assertEquals(entity.getAttachmentUrl(), vo.getAttachmentUrl());
        assertEquals(entity.getAttachmentSize(), vo.getAttachmentSize());
        assertEquals(entity.getCreateTime(), vo.getCreateTime());
        assertEquals(entity.getUpdateTime(), vo.getUpdateTime());
    }

    private SalesOrderVO salesOrder(String orderId, String status) {
        SalesOrderVO order = new SalesOrderVO();
        order.setOrderId(orderId);
        order.setStatus(status);
        order.setItems(List.of());
        return order;
    }

    private ProductionOrder fulfillment(String orderId, String salesOrderId, Integer process) {
        ProductionOrder order = new ProductionOrder();
        order.setOrderId(orderId);
        order.setSalesOrderId(salesOrderId);
        order.setStatus("producing");
        order.setProcess(process);
        return order;
    }
}

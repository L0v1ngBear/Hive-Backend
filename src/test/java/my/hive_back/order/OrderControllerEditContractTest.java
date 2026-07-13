package my.hive_back.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import my.hive_back.api.order.OrderController;
import my.hive_back.module.order.model.dto.UnifiedOrderUpdateRequest;
import my.hive_back.module.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderControllerEditContractTest {

    @Test
    void flowAdvanceForwardsOnlyTheRawFlowCodePathValue() {
        String rawFlowCode = "a".repeat(43);
        OrderService orderService = mock(OrderService.class);
        when(orderService.advanceByFlowCode(rawFlowCode)).thenReturn(Map.of("orderId", "SO-CONTROLLER-001"));
        OrderController controller = new OrderController();
        ReflectionTestUtils.setField(controller, "orderService", orderService);

        controller.advanceByFlowCode(rawFlowCode);

        verify(orderService).advanceByFlowCode(rawFlowCode);
    }

    @Test
    void updateAcceptsEverySharedEditFieldAndForwardsTheParsedRequest() throws Exception {
        UnifiedOrderUpdateRequest request = new ObjectMapper().readValue("""
                {
                  "status":"pending_ship",
                  "informationChannel":"partner",
                  "remark":"ready",
                  "customerName":"Acme",
                  "projectName":"Tower A",
                  "brandName":"Hive",
                  "orderCategory":"bulk",
                  "expressInfo":{"expressCompany":"SF","expressNo":"SF123"},
                  "items":[{"modelCode":"M-1","quantity":2,"weight":"12kg","spec":"2x3"}]
                }
                """, UnifiedOrderUpdateRequest.class);
        assertEquals("Acme", request.getCustomerName());
        assertEquals("Tower A", request.getProjectName());
        assertEquals("Hive", request.getBrandName());
        assertEquals("bulk", request.getOrderCategory());
        assertEquals("M-1", request.getItems().get(0).getModelCode());
        assertEquals("2x3", request.getItems().get(0).getSpec());

        OrderService orderService = mock(OrderService.class);
        when(orderService.update("SO-CONTROLLER-001", request)).thenReturn(Map.of("orderId", "SO-CONTROLLER-001"));
        OrderController controller = new OrderController();
        ReflectionTestUtils.setField(controller, "orderService", orderService);

        controller.update("SO-CONTROLLER-001", request);

        verify(orderService).update("SO-CONTROLLER-001", request);
    }
}

package my.hive_back.module.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive.common.order.OrderFlowCodeUtil;
import my.hive.common.print.PrintTaskService;
import my.hive_back.module.order.OrderCategoryEnum;
import my.hive_back.module.order.OrderStatusEnum;
import my.hive_back.module.order.mapper.ProductionOrderMapper;
import my.hive_back.module.order.mapper.SalesOrderDetailMapper;
import my.hive_back.module.order.mapper.SalesOrderMapper;
import my.hive_back.module.order.model.dto.OrderFlowPrintTaskRequest;
import my.hive_back.module.order.model.entity.ProductionOrder;
import my.hive_back.module.order.model.entity.SalesOrder;
import my.hive_back.module.order.model.entity.SalesOrderDetail;
import my.hive_back.module.order.model.vo.OrderFlowPrintTaskVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 订单流转码打印任务服务。
 * 网页端创建的任务和小程序现场打印，都统一落入 print_task 便于追溯。
 */
@Service
public class OrderFlowPrintService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    @Resource
    private SalesOrderMapper salesOrderMapper;

    @Resource
    private SalesOrderDetailMapper salesOrderDetailMapper;

    @Resource
    private ProductionOrderMapper productionOrderMapper;

    @Resource
    private PrintTaskService printTaskService;

    @Value("${ORDER_FLOW_CODE_SECRET:${AUTH_TOKEN_SECRET:hive-local-order-flow-secret}}")
    private String orderFlowCodeSecret;

    public OrderFlowPrintTaskVO createSalesTask(OrderFlowPrintTaskRequest request) {
        String orderId = requireOrderId(request);
        SalesOrder order = salesOrderMapper.selectOne(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getOrderId, orderId)
                .last("LIMIT 1"));
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        Map<String, Object> payload = buildSalesPayload(order);
        String taskNo = printTaskService.createTask(
                "order_flow",
                "sales_order",
                order.getOrderId(),
                order.getOrderId(),
                payload,
                null,
                null,
                "小程序创建销售订单流转码打印任务");
        if (!StringUtils.hasText(taskNo)) {
            throw new BusinessException("订单流转码打印任务创建失败");
        }
        payload.put("printTaskNo", taskNo);
        return buildVO(taskNo, order.getOrderId(), "sales", payload);
    }

    public OrderFlowPrintTaskVO createProductionTask(OrderFlowPrintTaskRequest request) {
        String orderId = requireOrderId(request);
        ProductionOrder order = productionOrderMapper.selectOne(new LambdaQueryWrapper<ProductionOrder>()
                .eq(ProductionOrder::getOrderId, orderId)
                .last("LIMIT 1"));
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        Map<String, Object> payload = buildProductionPayload(order);
        String taskNo = printTaskService.createTask(
                "order_flow",
                "production_order",
                order.getOrderId(),
                order.getSalesOrderId(),
                payload,
                null,
                null,
                "小程序创建生产订单流转码打印任务");
        if (!StringUtils.hasText(taskNo)) {
            throw new BusinessException("订单流转码打印任务创建失败");
        }
        payload.put("printTaskNo", taskNo);
        return buildVO(taskNo, order.getOrderId(), "production", payload);
    }

    private String requireOrderId(OrderFlowPrintTaskRequest request) {
        if (request == null || !StringUtils.hasText(request.getOrderId())) {
            throw new BusinessException("订单号不能为空");
        }
        return request.getOrderId().trim();
    }

    private Map<String, Object> buildSalesPayload(SalesOrder order) {
        SalesOrderDetail firstItem = salesOrderDetailMapper.selectOne(new LambdaQueryWrapper<SalesOrderDetail>()
                .eq(SalesOrderDetail::getOrderId, order.getOrderId())
                .orderByAsc(SalesOrderDetail::getId)
                .last("LIMIT 1"));
        Map<String, Object> payload = basePayload(
                order.getOrderId(),
                "sales",
                "销售订单",
                order.getStatus(),
                order.getOrderCategory(),
                order.getCustomerName(),
                order.getProjectName(),
                order.getBrandName(),
                firstItem == null ? order.getGoodsDesc() : firstItem.getModelCode());
        payload.put("deliveryDate", safeText(order.getDeliveryDate(), ""));
        payload.put("printReason", "销售订单流转码待打印");
        payload.put("flowQrPayload", buildQrPayload(payload));
        return payload;
    }

    private Map<String, Object> buildProductionPayload(ProductionOrder order) {
        Map<String, Object> payload = basePayload(
                order.getOrderId(),
                "production",
                "生产订单",
                order.getStatus(),
                order.getOrderCategory(),
                order.getCustomerName(),
                order.getProjectName(),
                order.getBrandName(),
                order.getModelCode());
        payload.put("salesOrderId", safeText(order.getSalesOrderId(), ""));
        payload.put("process", order.getProcess());
        payload.put("processText", order.getProcess() == null ? "" : "工序 " + (order.getProcess() + 1));
        payload.put("deliveryDate", order.getDeliveryDate() == null ? "" : DATE_TIME_FORMATTER.format(order.getDeliveryDate()));
        payload.put("printReason", "生产订单流转码待打印");
        payload.put("flowQrPayload", buildQrPayload(payload));
        return payload;
    }

    private Map<String, Object> basePayload(String orderId,
                                            String orderType,
                                            String orderTypeLabel,
                                            String currentStatus,
                                            String orderCategory,
                                            String customerName,
                                            String projectName,
                                            String brandName,
                                            String modelCode) {
        String normalizedCategory = OrderCategoryEnum.normalize(orderCategory);
        String safeOrderId = safeText(orderId, "");
        String flowCode = OrderFlowCodeUtil.generateFlowCode(
                orderFlowCodeSecret,
                TenantPermissionContext.getTenantCode(),
                orderType,
                safeOrderId
        );
        String flowScanCode = OrderFlowCodeUtil.buildScanCode(orderType, flowCode, safeOrderId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", safeOrderId);
        payload.put("barcode", flowScanCode);
        payload.put("flowCode", flowCode);
        payload.put("flowScanCode", flowScanCode);
        payload.put("flowBarcode", flowScanCode);
        payload.put("orderType", orderType);
        payload.put("orderTypeLabel", orderTypeLabel);
        payload.put("currentStatus", safeText(currentStatus, ""));
        payload.put("currentStatusText", statusLabel(currentStatus));
        payload.put("orderCategory", normalizedCategory);
        payload.put("orderCategoryLabel", categoryLabel(normalizedCategory));
        payload.put("customerName", safeText(customerName, "未填写客户"));
        payload.put("projectName", safeText(projectName, "未填写项目"));
        payload.put("brandName", safeText(brandName, "未填写品牌"));
        payload.put("modelCode", safeText(modelCode, "未填写型号"));
        payload.put("printDate", LocalDate.now().toString());
        payload.put("generatedAt", DATE_TIME_FORMATTER.format(LocalDateTime.now()));
        return payload;
    }

    private OrderFlowPrintTaskVO buildVO(String taskNo, String orderId, String orderType, Map<String, Object> payload) {
        OrderFlowPrintTaskVO vo = new OrderFlowPrintTaskVO();
        vo.setTaskNo(taskNo);
        vo.setOrderId(orderId);
        vo.setOrderType(orderType);
        vo.setPrintType("order_flow");
        vo.setPrintPayload(payload);
        return vo;
    }

    private String statusLabel(String status) {
        if (!StringUtils.hasText(status)) {
            return "扫码识别";
        }
        try {
            return OrderStatusEnum.getByCode(status.trim()).getName();
        } catch (Exception ignored) {
            return "cancelled".equals(status) ? "已取消" : status;
        }
    }

    private String categoryLabel(String category) {
        if (OrderCategoryEnum.SPECIAL_ORDER.getCode().equals(OrderCategoryEnum.normalize(category))) {
            return "特殊订单";
        }
        return switch (OrderCategoryEnum.normalize(category)) {
            case "sample_room" -> "样板间";
            case "replenishment" -> "补单";
            case "drawing_budget" -> "图纸预算";
            default -> "大货";
        };
    }

    private String buildQrPayload(Map<String, Object> payload) {
        Map<String, Object> qrPayload = new LinkedHashMap<>();
        qrPayload.put("version", "1");
        qrPayload.put("codeType", "order_flow");
        qrPayload.put("orderType", stringValue(payload.get("orderType")));
        qrPayload.put("orderId", stringValue(payload.get("orderId")));
        qrPayload.put("flowCode", stringValue(payload.get("flowCode")));
        qrPayload.put("flowScanCode", stringValue(payload.get("flowScanCode")));
        qrPayload.put("generatedAt", stringValue(payload.get("generatedAt")));
        return toSimpleJson(qrPayload);
    }

    private String toSimpleJson(Map<String, Object> source) {
        return source.entrySet().stream()
                .map(entry -> "\"" + jsonEscape(entry.getKey()) + "\":\"" + jsonEscape(stringValue(entry.getValue())) + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }

    private String jsonEscape(String value) {
        return stringValue(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private String safeText(Object value, String fallback) {
        String text = stringValue(value);
        return StringUtils.hasText(text) ? text : fallback;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}

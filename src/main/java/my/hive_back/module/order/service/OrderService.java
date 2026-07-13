package my.hive_back.module.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import my.hive.common.annotation.RequirePermission;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.dto.PageResult;
import my.hive.common.exception.BusinessException;
import my.hive.common.order.OrderFlowCodeUtil;
import my.hive_back.module.order.model.dto.BaseOrderListRequest;
import my.hive_back.module.order.model.dto.OrderFlowPrintTaskRequest;
import my.hive_back.module.order.model.dto.ProductionOrderUpdateRequest;
import my.hive_back.module.order.model.dto.SalesOrderAddRequest;
import my.hive_back.module.order.model.dto.SalesOrderListRequest;
import my.hive_back.module.order.model.dto.SalesOrderUpdateRequest;
import my.hive_back.module.order.model.dto.UnifiedOrderUpdateRequest;
import my.hive_back.module.order.model.entity.ProductionOrder;
import my.hive_back.module.order.model.entity.SalesOrderStatusLog;
import my.hive_back.module.order.model.vo.OrderFlowPrintTaskVO;
import my.hive_back.module.order.model.vo.ProductionOrderVO;
import my.hive_back.module.order.model.vo.SalesOrderVO;
import my.hive_back.module.sys.model.enums.PermissionCodeEnum;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Canonical order facade. Sales orders are the single business-order record;
 * linked production rows are fulfillment details and never become list rows.
 */
@Service
public class OrderService {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;
    private static final List<String> FULFILLMENT_VIEW_FIELDS = List.of(
            "process",
            "processText",
            "currentProcessText",
            "completedProcessText",
            "processProgressPercent",
            "processSteps"
    );

    @Resource
    private SalesOrderService salesOrderService;

    @Resource
    private ProductionOrderService productionOrderService;

    @Resource
    private OrderFlowPrintService orderFlowPrintService;

    @Value("${ORDER_FLOW_CODE_SECRET:${AUTH_TOKEN_SECRET:hive-local-order-flow-secret}}")
    private String orderFlowCodeSecret;

    @RequirePermission(value = PermissionCodeEnum.CODE_ORDER_LIST, message = "您没有权限查询订单列表")
    public PageResult<Map<String, Object>> page(BaseOrderListRequest request) {
        BaseOrderListRequest safeRequest = request == null ? new BaseOrderListRequest() : request;
        int pageNum = safePageNum(safeRequest.getPageNum());
        int pageSize = safePageSize(safeRequest.getPageSize());
        safeRequest.setPageNum(pageNum);
        safeRequest.setPageSize(pageSize);

        Page<SalesOrderVO> canonicalPage = salesOrderService.selectSalesOrder(salesRequest(safeRequest));
        List<Map<String, Object>> rows = canonicalPage.getRecords().stream()
                .map(this::beanToMap)
                .collect(Collectors.toCollection(ArrayList::new));
        enrichFulfillment(rows);

        PageResult<Map<String, Object>> result = new PageResult<>();
        result.setCurrent(canonicalPage.getCurrent());
        result.setSize(canonicalPage.getSize());
        result.setTotal(canonicalPage.getTotal());
        result.setPages(canonicalPage.getPages());
        result.setData(rows);
        return result;
    }

    @RequirePermission(value = PermissionCodeEnum.CODE_ORDER_LIST, message = "您没有权限查询订单统计")
    public Map<String, Long> countStatuses() {
        return salesOrderService.countSalesOrderStatuses();
    }

    @RequirePermission(value = PermissionCodeEnum.CODE_ORDER_DETAIL, message = "您没有权限查询订单详情")
    public Map<String, Object> detail(@NotBlank String orderId) {
        String canonicalOrderId = normalizeOrderId(orderId);
        Map<String, Object> row = beanToMap(salesOrderService.getByIdandTenantId(canonicalOrderId));
        String signedCode = OrderFlowCodeUtil.generateFlowCode(
                orderFlowCodeSecret, TenantPermissionContext.getTenantCode(), "sales", canonicalOrderId);
        row.put("flowCode", OrderFlowCodeUtil.buildScanCode("sales", signedCode, canonicalOrderId));
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row);
        enrichFulfillment(rows);
        return row;
    }

    @RequirePermission(value = PermissionCodeEnum.CODE_ORDER_DETAIL, message = "您没有权限查询订单状态变更日志")
    public List<Map<String, Object>> statusLog(@NotBlank String orderId) {
        return salesOrderService.selectSalesOrderStatusLog(normalizeOrderId(orderId)).stream()
                .map(this::salesLog)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    @RequirePermission(value = PermissionCodeEnum.CODE_ORDER_LIST, message = "您没有权限更新订单")
    public Map<String, Object> update(@NotBlank String orderId, @Valid UnifiedOrderUpdateRequest request) {
        String canonicalOrderId = normalizeOrderId(orderId);
        if (request == null) {
            throw new BusinessException(400, "订单更新内容不能为空");
        }
        SalesOrderVO current = salesOrderService.getByIdandTenantId(canonicalOrderId);
        boolean processUpdate = request.getProcess() != null;
        boolean statusChanged = StringUtils.hasText(request.getStatus())
                && !request.getStatus().trim().equals(current.getStatus());
        boolean contentUpdate = hasEditableContent(request);
        if (!statusChanged && !processUpdate && !contentUpdate) {
            throw new BusinessException(400, "订单更新内容不能为空");
        }

        if (contentUpdate) {
            salesOrderService.updateEditableContent(canonicalOrderId, request);
        }

        if (processUpdate) {
            productionOrderService.updateLinkedStatusAndProcess(canonicalOrderId, productionUpdateRequest(request));
        }

        if (statusChanged) {
            salesOrderService.updateStatusAndProcess(canonicalOrderId, salesTransitionRequest(request));
        }
        return detail(canonicalOrderId);
    }

    @Transactional(rollbackFor = Exception.class)
    @RequirePermission(value = PermissionCodeEnum.CODE_ORDER_LIST, message = "您没有权限提交订单回退审批")
    public Map<String, Object> submitRollback(@NotBlank String orderId, UnifiedOrderUpdateRequest request) {
        String canonicalOrderId = normalizeOrderId(orderId);
        salesOrderService.submitRollbackApproval(canonicalOrderId,
                request == null ? new SalesOrderUpdateRequest() : salesUpdateRequest(request));
        return detail(canonicalOrderId);
    }

    @Transactional(rollbackFor = Exception.class)
    @RequirePermission(value = PermissionCodeEnum.CODE_ORDER_LIST, message = "您没有权限推进订单")
    public Map<String, Object> advanceByFlowCode(@NotBlank String flowCode) {
        String orderId = salesOrderService.advanceByFlowCode(flowCode).getOrderId();
        return detail(orderId);
    }

    @Transactional(rollbackFor = Exception.class)
    @RequirePermission(value = PermissionCodeEnum.CODE_ORDER_CREATE, message = "您没有权限添加订单")
    public void add(@Valid SalesOrderAddRequest request) {
        if (request == null) {
            throw new BusinessException(400, "订单内容不能为空");
        }
        request.setCreateProductionOrder(1);
        salesOrderService.addSalesOrder(request);
    }

    @RequirePermission(value = PermissionCodeEnum.CODE_ORDER_LIST, message = "您没有权限生成订单流转码")
    public OrderFlowPrintTaskVO createFlowPrintTask(@Valid OrderFlowPrintTaskRequest request) {
        return orderFlowPrintService.createSalesTask(request);
    }

    private void enrichFulfillment(List<Map<String, Object>> rows) {
        List<String> orderIds = rows.stream()
                .map(row -> String.valueOf(row.getOrDefault("orderId", "")).trim())
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        Map<String, List<ProductionOrder>> fulfillmentByOrder = productionOrderService.listBySalesOrderIds(orderIds)
                .stream()
                .filter(order -> StringUtils.hasText(order.getSalesOrderId()))
                .collect(Collectors.groupingBy(ProductionOrder::getSalesOrderId, LinkedHashMap::new, Collectors.toList()));

        for (Map<String, Object> row : rows) {
            String orderId = String.valueOf(row.getOrDefault("orderId", ""));
            List<ProductionOrder> fulfillmentRows = fulfillmentByOrder.getOrDefault(orderId, List.of());
            row.put("fulfillmentTracked", !fulfillmentRows.isEmpty());
            row.put("fulfillmentRecordCount", fulfillmentRows.size());
            if (fulfillmentRows.isEmpty()) {
                continue;
            }
            ProductionOrder representative = fulfillmentRows.stream()
                    .min(Comparator.comparingInt(item -> item.getProcess() == null ? -1 : item.getProcess()))
                    .orElse(fulfillmentRows.get(0));
            ProductionOrder snapshot = new ProductionOrder();
            BeanUtils.copyProperties(representative, snapshot);
            snapshot.setStatus(String.valueOf(row.getOrDefault("status", representative.getStatus())));
            ProductionOrderVO fulfillmentView = productionOrderService.toVO(snapshot);
            Map<String, Object> fulfillmentMap = beanToMap(fulfillmentView);
            for (String field : FULFILLMENT_VIEW_FIELDS) {
                row.put(field, fulfillmentMap.get(field));
            }
        }
    }

    private SalesOrderListRequest salesRequest(BaseOrderListRequest source) {
        SalesOrderListRequest target = new SalesOrderListRequest();
        target.setStatus(source.getStatus());
        target.setKeyWord(source.getKeyWord());
        target.setOrderCategory(source.getOrderCategory());
        target.setIsInvoice(source.getIsInvoice());
        target.setPageNum(source.getPageNum());
        target.setPageSize(source.getPageSize());
        return target;
    }

    private SalesOrderUpdateRequest salesUpdateRequest(UnifiedOrderUpdateRequest source) {
        SalesOrderUpdateRequest target = new SalesOrderUpdateRequest();
        target.setStatus(source.getStatus());
        target.setRemark(source.getRemark());
        target.setInformationChannel(source.getInformationChannel());
        target.setAuditorIds(source.getAuditorIds());
        target.setIsInvoice(source.getIsInvoice());
        if (source.getExpressInfo() != null) {
            SalesOrderUpdateRequest.ExpressInfo expressInfo = new SalesOrderUpdateRequest.ExpressInfo();
            expressInfo.setExpressCompany(source.getExpressInfo().getExpressCompany());
            expressInfo.setExpressNo(source.getExpressInfo().getExpressNo());
            target.setExpressInfo(expressInfo);
        }
        return target;
    }

    private SalesOrderUpdateRequest salesTransitionRequest(UnifiedOrderUpdateRequest source) {
        SalesOrderUpdateRequest target = new SalesOrderUpdateRequest();
        target.setStatus(source.getStatus());
        target.setAuditorIds(source.getAuditorIds());
        return target;
    }

    private boolean hasEditableContent(UnifiedOrderUpdateRequest request) {
        return request.getRemark() != null
                || request.getInformationChannel() != null
                || request.getExpressInfo() != null
                || request.getIsInvoice() != null
                || request.getCustomerName() != null
                || request.getProjectName() != null
                || request.getBrandName() != null
                || request.getOrderCategory() != null
                || request.getItems() != null;
    }

    private ProductionOrderUpdateRequest productionUpdateRequest(UnifiedOrderUpdateRequest source) {
        ProductionOrderUpdateRequest target = new ProductionOrderUpdateRequest();
        target.setStatus(source.getStatus());
        target.setProcess(source.getProcess());
        target.setOperateType(source.getOperateType());
        target.setRemark(source.getRemark());
        target.setAuditorIds(source.getAuditorIds());
        return target;
    }

    private Map<String, Object> salesLog(SalesOrderStatusLog log) {
        return beanToMap(log);
    }

    private Map<String, Object> beanToMap(Object source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        BeanWrapperImpl wrapper = new BeanWrapperImpl(source);
        for (PropertyDescriptor descriptor : wrapper.getPropertyDescriptors()) {
            String name = descriptor.getName();
            if ("class".equals(name) || !wrapper.isReadableProperty(name)) {
                continue;
            }
            result.put(name, wrapper.getPropertyValue(name));
        }
        return result;
    }

    private int safePageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? DEFAULT_PAGE_NUM : pageNum;
    }

    private int safePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String normalizeOrderId(String orderId) {
        if (!StringUtils.hasText(orderId)) {
            throw new BusinessException(400, "订单编号不能为空");
        }
        return orderId.trim();
    }
}

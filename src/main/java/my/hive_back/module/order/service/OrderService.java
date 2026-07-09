package my.hive_back.module.order.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import my.hive.common.dto.PageResult;
import my.hive.common.exception.BusinessException;
import my.hive_back.module.order.model.dto.BaseOrderListRequest;
import my.hive_back.module.order.model.dto.ProductionOrderListRequest;
import my.hive_back.module.order.model.dto.SalesOrderListRequest;
import my.hive_back.module.order.model.entity.ProductionOrder;
import my.hive_back.module.order.model.entity.ProductionOrderStatusLog;
import my.hive_back.module.order.model.entity.SalesOrderStatusLog;
import my.hive_back.module.order.model.vo.ProductionOrderVO;
import my.hive_back.module.order.model.vo.SalesOrderVO;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;

import java.beans.PropertyDescriptor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderService {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;
    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    );

    @Resource
    private SalesOrderService salesOrderService;

    @Resource
    private ProductionOrderService productionOrderService;

    public PageResult<Map<String, Object>> page(BaseOrderListRequest request) {
        BaseOrderListRequest safeRequest = request == null ? new BaseOrderListRequest() : request;
        int pageNum = safePageNum(safeRequest.getPageNum());
        int pageSize = safePageSize(safeRequest.getPageSize());
        safeRequest.setPageNum(pageNum);
        safeRequest.setPageSize(pageSize);
        SalesOrderListRequest salesRequest = salesRequest(safeRequest);
        ProductionOrderListRequest productionRequest = productionRequest(safeRequest);

        Page<SalesOrderVO> salesPage = salesOrderService.selectSalesOrder(salesRequest);
        IPage<ProductionOrder> productionPage = productionOrderService.selectProductionOrder(productionRequest);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (SalesOrderVO item : salesPage.getRecords()) {
            Map<String, Object> row = beanToMap(item);
            row.put("orderType", "sales");
            rows.add(row);
        }
        for (ProductionOrder item : productionPage.getRecords()) {
            ProductionOrderVO vo = productionOrderService.toVO(item);
            Map<String, Object> row = beanToMap(vo);
            row.put("orderType", "production");
            rows.add(row);
        }

        rows.sort((left, right) -> compareRows(right, left));
        long total = safeTotal(salesPage.getTotal()) + safeTotal(productionPage.getTotal());
        int end = (int) Math.min(rows.size(), pageSize);

        PageResult<Map<String, Object>> result = new PageResult<>();
        result.setCurrent((long) pageNum);
        result.setSize((long) pageSize);
        result.setTotal(total);
        result.setPages(pageSize <= 0 ? 0L : (total + pageSize - 1) / pageSize);
        result.setData(rows.subList(0, end));
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

    public Map<String, Long> countStatuses() {
        Map<String, Long> result = new LinkedHashMap<>();
        merge(result, salesOrderService.countSalesOrderStatuses());
        merge(result, productionOrderService.countProductionOrderStatuses());
        return result;
    }

    public Map<String, Object> detail(@NotBlank String orderId) {
        return isSalesOrder(orderId) ? salesDetail(orderId) : productionDetail(orderId);
    }

    public List<Map<String, Object>> statusLog(@NotBlank String orderId) {
        if (isSalesOrder(orderId)) {
            return salesOrderService.selectSalesOrderStatusLog(normalizeOrderId(orderId)).stream()
                    .map(this::salesLog)
                    .toList();
        }
        return productionOrderService.selectOrderStausLog(normalizeOrderId(orderId)).stream()
                .map(this::productionLog)
                .toList();
    }

    private Map<String, Object> salesDetail(String orderId) {
        SalesOrderVO order = salesOrderService.getByIdandTenantId(normalizeOrderId(orderId));
        Map<String, Object> row = beanToMap(order);
        row.put("orderType", "sales");
        return row;
    }

    private Map<String, Object> productionDetail(String orderId) {
        ProductionOrder order = productionOrderService.selectProductionOrderDetail(normalizeOrderId(orderId));
        Map<String, Object> row = beanToMap(productionOrderService.toVO(order));
        row.put("orderType", "production");
        return row;
    }

    private Map<String, Object> salesLog(SalesOrderStatusLog log) {
        Map<String, Object> row = beanToMap(log);
        row.put("orderType", "sales");
        return row;
    }

    private Map<String, Object> productionLog(ProductionOrderStatusLog log) {
        Map<String, Object> row = beanToMap(log);
        row.put("orderType", "production");
        return row;
    }

    private SalesOrderListRequest salesRequest(BaseOrderListRequest request) {
        SalesOrderListRequest target = new SalesOrderListRequest();
        copyRequest(request, target);
        return target;
    }

    private ProductionOrderListRequest productionRequest(BaseOrderListRequest request) {
        ProductionOrderListRequest target = new ProductionOrderListRequest();
        copyRequest(request, target);
        target.setIsInvoice(null);
        return target;
    }

    private void copyRequest(BaseOrderListRequest source, BaseOrderListRequest target) {
        target.setStatus(source.getStatus());
        target.setKeyWord(source.getKeyWord());
        target.setOrderCategory(source.getOrderCategory());
        target.setIsInvoice(source.getIsInvoice());
        target.setPageNum(source.getPageNum());
        target.setPageSize(source.getPageSize());
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

    private void merge(Map<String, Long> target, Map<String, Long> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        source.forEach((key, value) -> target.merge(key, value == null ? 0L : value, Long::sum));
    }

    private int compareRows(Map<String, Object> left, Map<String, Object> right) {
        long leftTime = timestamp(left.get("createTime"));
        long rightTime = timestamp(right.get("createTime"));
        int timeCompare = Long.compare(leftTime, rightTime);
        if (timeCompare != 0) {
            return timeCompare;
        }
        return String.valueOf(left.getOrDefault("orderId", ""))
                .compareTo(String.valueOf(right.getOrDefault("orderId", "")));
    }

    private long timestamp(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.atZone(ZONE_ID).toInstant().toEpochMilli();
        }
        if (value instanceof LocalDate date) {
            return date.atStartOfDay(ZONE_ID).toInstant().toEpochMilli();
        }
        if (value == null) {
            return 0L;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return 0L;
        }
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(text.replace('T', ' '), formatter)
                        .atZone(ZONE_ID)
                        .toInstant()
                        .toEpochMilli();
            } catch (RuntimeException ignored) {
                // try the next supported format
            }
        }
        try {
            return LocalDate.parse(text.substring(0, Math.min(10, text.length())))
                    .atStartOfDay(ZONE_ID)
                    .toInstant()
                    .toEpochMilli();
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private long safeTotal(Long value) {
        return value == null ? 0L : value;
    }

    private boolean isSalesOrder(String orderId) {
        String normalized = normalizeOrderId(orderId).toUpperCase();
        return normalized.startsWith("SO") || normalized.contains("-SO-") || normalized.contains("_SO_");
    }

    private String normalizeOrderId(String orderId) {
        if (orderId == null || orderId.trim().isEmpty()) {
            throw new BusinessException(400, "订单编号不能为空");
        }
        return orderId.trim();
    }
}

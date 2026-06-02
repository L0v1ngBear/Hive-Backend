package my.hive_back.module.todo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.dto.PageResult;
import my.hive_back.common.enums.BinaryFlagEnum;
import my.hive_back.module.badproduct.BadProductStatusEnum;
import my.hive_back.module.badproduct.mapper.BadProductMapper;
import my.hive_back.module.badproduct.model.entity.BadProductRecord;
import my.hive_back.module.finance.FinanceApprovalStatusEnum;
import my.hive_back.module.finance.mapper.FinanceApprovalMapper;
import my.hive_back.module.finance.model.entity.FinanceApproval;
import my.hive_back.module.inventory.mapper.OutboundOrderMapper;
import my.hive_back.module.inventory.model.entity.OutboundOrder;
import my.hive_back.module.leave.LeaveStatusEnum;
import my.hive_back.module.leave.mapper.LeaveMapper;
import my.hive_back.module.leave.model.entity.UserLeave;
import my.hive_back.module.order.mapper.ProductionOrderMapper;
import my.hive_back.module.order.mapper.SalesOrderMapper;
import my.hive_back.module.order.OrderStatusEnum;
import my.hive_back.module.order.model.entity.ProductionOrder;
import my.hive_back.module.order.model.entity.SalesOrder;
import my.hive_back.module.sys.model.enums.PermissionCodeEnum;
import my.hive_back.module.todo.mapper.TodoNotificationMapper;
import my.hive_back.module.todo.model.dto.TodoPageRequest;
import my.hive_back.module.todo.model.vo.TodoItemVO;
import my.hive_back.module.todo.model.vo.TodoNotificationRow;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 待办中心服务，统一聚合“当前用户需要处理”的审批、订单、打印和质量事项。
 */
@Service
public class TodoService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int HOME_CATEGORY_LIMIT = 8;

    @Resource
    private LeaveMapper leaveMapper;

    @Resource
    private FinanceApprovalMapper financeApprovalMapper;

    @Resource
    private ProductionOrderMapper productionOrderMapper;

    @Resource
    private SalesOrderMapper salesOrderMapper;

    @Resource
    private OutboundOrderMapper outboundOrderMapper;

    @Resource
    private BadProductMapper badProductMapper;

    @Resource
    private TodoNotificationMapper todoNotificationMapper;

    public PageResult<TodoItemVO> page(TodoPageRequest request) {
        List<TodoItemVO> allTodos = listAll(request.getType());
        long pageNum = normalize(request.getPageNum(), 1L);
        long pageSize = normalizePageSize(request.getPageSize());
        long from = Math.min((pageNum - 1) * pageSize, allTodos.size());
        long to = Math.min(from + pageSize, allTodos.size());

        PageResult<TodoItemVO> result = new PageResult<>();
        result.setCurrent(pageNum);
        result.setSize(pageSize);
        result.setTotal((long) allTodos.size());
        result.setPages((allTodos.size() + pageSize - 1) / pageSize);
        result.setData(allTodos.subList((int) from, (int) to));
        return result;
    }

    public List<TodoItemVO> listHomeTodos(int limit) {
        return listAll("all", HOME_CATEGORY_LIMIT).stream().limit(limit).toList();
    }

    public int countAll() {
        Long userId = TenantPermissionContext.getUserId();
        String tenantCode = currentTenantCode();
        if (tenantCode == null) {
            return 0;
        }
        String userIdText = currentUserIdText();
        long total = 0L;
        if (userId != null) {
            total += nvl(todoNotificationMapper.countPending(tenantCode, userId));
            total += leaveMapper.selectCount(new LambdaQueryWrapper<UserLeave>()
                    .eq(UserLeave::getTenantCode, tenantCode)
                    .eq(UserLeave::getAuditorId, userId)
                    .eq(UserLeave::getStatus, LeaveStatusEnum.PENDING.getCode()));
            total += financeApprovalMapper.selectCount(new LambdaQueryWrapper<FinanceApproval>()
                    .eq(FinanceApproval::getTenantCode, tenantCode)
                    .eq(FinanceApproval::getAuditorId, userId)
                    .eq(FinanceApproval::getStatus, FinanceApprovalStatusEnum.PENDING.getCode()));
        }
        if (hasAnyPermission(PermissionCodeEnum.CODE_PRODUCTION_ORDER_LIST, PermissionCodeEnum.CODE_PRODUCTION_ORDER_ALL, PermissionCodeEnum.CODE_ALL)) {
            total += productionOrderMapper.selectCount(new LambdaQueryWrapper<ProductionOrder>()
                    .eq(ProductionOrder::getTenantCode, tenantCode)
                    .and(wrapper -> wrapper.eq(ProductionOrder::getCreator, userIdText)
                            .or()
                            .eq(ProductionOrder::getUpdater, userIdText))
                    .in(ProductionOrder::getStatus, List.of(OrderStatusEnum.PENDING_CONFIRM.getCode(), OrderStatusEnum.PENDING_MATERIAL.getCode(), OrderStatusEnum.PENDING_SHIP.getCode())));
        }
        if (hasAnyPermission(PermissionCodeEnum.CODE_SALES_ORDER_LIST, PermissionCodeEnum.CODE_SALES_ORDER_ALL, PermissionCodeEnum.CODE_ALL)) {
            total += salesOrderMapper.selectCount(new LambdaQueryWrapper<SalesOrder>()
                    .eq(SalesOrder::getTenantCode, tenantCode)
                    .and(wrapper -> wrapper.eq(SalesOrder::getCreator, userIdText)
                            .or()
                            .eq(SalesOrder::getUpdater, userIdText))
                    .in(SalesOrder::getStatus, List.of(OrderStatusEnum.PENDING_CONFIRM.getCode(), OrderStatusEnum.PENDING_SHIP.getCode())));
        }
        if (hasAnyPermission(PermissionCodeEnum.CODE_INVENTORY, PermissionCodeEnum.CODE_INVENTORY_ALL, PermissionCodeEnum.CODE_INVENTORY_CLOTH_OUT, PermissionCodeEnum.CODE_ALL) && userId != null) {
            total += outboundOrderMapper.selectCount(new LambdaQueryWrapper<OutboundOrder>()
                    .eq(OutboundOrder::getTenantCode, tenantCode)
                    .eq(OutboundOrder::getOperatorId, userId)
                    .eq(OutboundOrder::getPrintStatus, BinaryFlagEnum.NO.getCode()));
        }
        if (hasAnyPermission(PermissionCodeEnum.CODE_BADPRODUCT_ALL, PermissionCodeEnum.CODE_BADPRODUCT_LIST, PermissionCodeEnum.CODE_BADPRODUCT_PROCESS, PermissionCodeEnum.CODE_ALL) && userId != null) {
            total += badProductMapper.selectCount(new LambdaQueryWrapper<BadProductRecord>()
                    .eq(BadProductRecord::getTenantCode, tenantCode)
                    .eq(BadProductRecord::getCreatorId, userId)
                    .eq(BadProductRecord::getStatus, BadProductStatusEnum.PENDING.getCode()));
        }
        return Math.toIntExact(Math.min(total, Integer.MAX_VALUE));
    }

    private List<TodoItemVO> listAll(String type) {
        return listAll(type, null);
    }

    private List<TodoItemVO> listAll(String type, Integer limitPerCategory) {
        Long userId = TenantPermissionContext.getUserId();
        String tenantCode = currentTenantCode();
        if (tenantCode == null) {
            return List.of();
        }
        String userIdText = currentUserIdText();
        List<TodoItemVO> todos = new ArrayList<>();

        if (matchesNotificationType(type) && userId != null) {
            todos.addAll(buildNotificationTodos(tenantCode, userId, type, limitPerCategory));
        }
        if (matches(type, "approval") && userId != null) {
            todos.addAll(buildLeaveTodos(tenantCode, userId, limitPerCategory));
            todos.addAll(buildFinanceTodos(tenantCode, userId, limitPerCategory));
        }
        if (matches(type, "order")) {
            todos.addAll(buildProductionTodos(tenantCode, userIdText, limitPerCategory));
            todos.addAll(buildSalesTodos(tenantCode, userIdText, limitPerCategory));
        }
        if (matches(type, "print") && userId != null) {
            todos.addAll(buildOutboundPrintTodos(tenantCode, userId, limitPerCategory));
        }
        if (matches(type, "quality") && userId != null) {
            todos.addAll(buildBadProductTodos(tenantCode, userId, limitPerCategory));
        }

        todos.sort(Comparator.comparing(TodoItemVO::getSortTime).reversed());
        return todos;
    }

    private List<TodoItemVO> buildNotificationTodos(String tenantCode, Long userId, String type, Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? MAX_PAGE_SIZE : Math.min(limit, MAX_PAGE_SIZE);
        return todoNotificationMapper.selectPending(tenantCode, userId, safeLimit).stream()
                .filter(item -> matchesNotificationFilter(type, item))
                .map(item -> buildTodo(
                        "notification-" + item.getId(),
                        resolveNotificationTodoType(item),
                        resolveNotificationTag(item),
                        firstText(item.getTitle(), "业务提醒"),
                        firstText(item.getContent(), "请进入对应业务页面处理"),
                        item.getBizId(),
                        resolveMiniRoute(item),
                        firstNotNull(item.getUpdateTime(), item.getCreateTime())
                ))
                .toList();
    }

    private List<TodoItemVO> buildLeaveTodos(String tenantCode, Long userId, Integer limit) {
        List<UserLeave> leaves = leaveMapper.selectList(withLimit(new LambdaQueryWrapper<UserLeave>()
                .eq(UserLeave::getTenantCode, tenantCode)
                .eq(UserLeave::getAuditorId, userId)
                .eq(UserLeave::getStatus, LeaveStatusEnum.PENDING.getCode())
                .orderByDesc(UserLeave::getCreateTime), limit));

        return leaves.stream().map(item -> buildTodo(
                "leave-" + item.getLeaveCode(),
                "leave",
                "请假审批",
                "请审批请假单",
                item.getLeaveCode(),
                item.getLeaveCode(),
                "/pages/index/index",
                item.getCreateTime()
        )).toList();
    }

    private List<TodoItemVO> buildFinanceTodos(String tenantCode, Long userId, Integer limit) {
        List<FinanceApproval> approvals = financeApprovalMapper.selectList(withLimit(new LambdaQueryWrapper<FinanceApproval>()
                .eq(FinanceApproval::getTenantCode, tenantCode)
                .eq(FinanceApproval::getAuditorId, userId)
                .eq(FinanceApproval::getStatus, FinanceApprovalStatusEnum.PENDING.getCode())
                .orderByDesc(FinanceApproval::getCreateTime), limit));

        return approvals.stream().map(item -> buildTodo(
                "finance-" + item.getApprovalCode(),
                "finance",
                "财务审批",
                "请审批财务单",
                item.getApprovalCode(),
                item.getApprovalCode(),
                "/pages/index/index",
                item.getCreateTime()
        )).toList();
    }

    private List<TodoItemVO> buildProductionTodos(String tenantCode, String userId, Integer limit) {
        if (!hasAnyPermission(PermissionCodeEnum.CODE_PRODUCTION_ORDER_LIST, PermissionCodeEnum.CODE_PRODUCTION_ORDER_ALL, PermissionCodeEnum.CODE_ALL)) {
            return List.of();
        }
        List<ProductionOrder> orders = productionOrderMapper.selectList(withLimit(new LambdaQueryWrapper<ProductionOrder>()
                .eq(ProductionOrder::getTenantCode, tenantCode)
                .and(wrapper -> wrapper.eq(ProductionOrder::getCreator, userId)
                        .or()
                        .eq(ProductionOrder::getUpdater, userId))
                .in(ProductionOrder::getStatus, List.of(OrderStatusEnum.PENDING_CONFIRM.getCode(), OrderStatusEnum.PENDING_MATERIAL.getCode(), OrderStatusEnum.PENDING_SHIP.getCode()))
                .orderByDesc(ProductionOrder::getUpdateTime), limit));

        return orders.stream().map(item -> buildTodo(
                "production-" + item.getOrderId(),
                "production",
                "生产订单",
                item.getOrderId() + " 当前状态：" + statusText(item.getStatus()),
                item.getOrderId(),
                item.getOrderId(),
                "/pages/orderDetail/orderDetail?orderId=" + item.getOrderId(),
                firstNotNull(item.getUpdateTime(), item.getCreateTime())
        )).toList();
    }

    private List<TodoItemVO> buildSalesTodos(String tenantCode, String userId, Integer limit) {
        if (!hasAnyPermission(PermissionCodeEnum.CODE_SALES_ORDER_LIST, PermissionCodeEnum.CODE_SALES_ORDER_ALL, PermissionCodeEnum.CODE_ALL)) {
            return List.of();
        }
        List<SalesOrder> orders = salesOrderMapper.selectList(withLimit(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getTenantCode, tenantCode)
                .and(wrapper -> wrapper.eq(SalesOrder::getCreator, userId)
                        .or()
                        .eq(SalesOrder::getUpdater, userId))
                .in(SalesOrder::getStatus, List.of(OrderStatusEnum.PENDING_CONFIRM.getCode(), OrderStatusEnum.PENDING_SHIP.getCode()))
                .orderByDesc(SalesOrder::getUpdateTime), limit));

        return orders.stream().map(item -> buildTodo(
                "sales-" + item.getOrderId(),
                "sales",
                "销售订单",
                item.getOrderId() + " 当前状态：" + statusText(item.getStatus()),
                item.getOrderId(),
                item.getOrderId(),
                "/pages/orderDetail/orderDetail?type=sales&orderId=" + item.getOrderId(),
                firstNotNull(item.getUpdateTime(), item.getCreateTime())
        )).toList();
    }

    private List<TodoItemVO> buildOutboundPrintTodos(String tenantCode, Long userId, Integer limit) {
        if (!hasAnyPermission(PermissionCodeEnum.CODE_INVENTORY, PermissionCodeEnum.CODE_INVENTORY_ALL, PermissionCodeEnum.CODE_INVENTORY_CLOTH_OUT, PermissionCodeEnum.CODE_ALL)) {
            return List.of();
        }
        List<OutboundOrder> orders = outboundOrderMapper.selectList(withLimit(new LambdaQueryWrapper<OutboundOrder>()
                .eq(OutboundOrder::getTenantCode, tenantCode)
                .eq(OutboundOrder::getOperatorId, userId)
                .eq(OutboundOrder::getPrintStatus, BinaryFlagEnum.NO.getCode())
                .orderByDesc(OutboundOrder::getCreateTime), limit));

        return orders.stream().map(item -> buildTodo(
                "outbound-" + item.getOrderNo(),
                "outbound",
                "出库打印",
                "出库单待打印",
                item.getOrderNo(),
                item.getOrderNo(),
                "/pages/inventory/inventory",
                firstNotNull(item.getUpdateTime(), item.getCreateTime())
        )).toList();
    }

    private List<TodoItemVO> buildBadProductTodos(String tenantCode, Long userId, Integer limit) {
        if (!hasAnyPermission(PermissionCodeEnum.CODE_BADPRODUCT_ALL, PermissionCodeEnum.CODE_BADPRODUCT_LIST, PermissionCodeEnum.CODE_BADPRODUCT_PROCESS, PermissionCodeEnum.CODE_ALL)) {
            return List.of();
        }
        List<BadProductRecord> records = badProductMapper.selectList(withLimit(new LambdaQueryWrapper<BadProductRecord>()
                .eq(BadProductRecord::getTenantCode, tenantCode)
                .eq(BadProductRecord::getCreatorId, userId)
                .eq(BadProductRecord::getStatus, BadProductStatusEnum.PENDING.getCode())
                .orderByDesc(BadProductRecord::getCreateTime), limit));

        return records.stream().map(item -> buildTodo(
                "badProduct-" + item.getDefectiveId(),
                "badProduct",
                "质量处理",
                "质量记录待处理",
                item.getDefectiveId(),
                item.getDefectiveId(),
                "/pages/badProduct/badProduct",
                firstNotNull(item.getUpdateTime(), item.getCreateTime())
        )).toList();
    }

    private TodoItemVO buildTodo(String id, String type, String tag, String title, String content,
                                 String bizId, String route, LocalDateTime time) {
        TodoItemVO item = new TodoItemVO();
        item.setId(id);
        item.setType(type);
        item.setTag(tag);
        item.setTitle(title);
        item.setContent(content);
        item.setBizId(bizId);
        item.setRoute(route);
        item.setTime(time == null ? "--" : time.format(TIME_FORMATTER));
        item.setSortTime(time == null ? 0L : time.atZone(ZONE_ID).toInstant().toEpochMilli());
        return item;
    }

    private boolean matches(String requestType, String targetType) {
        return requestType == null || requestType.isBlank() || "all".equalsIgnoreCase(requestType)
                || targetType.equalsIgnoreCase(requestType);
    }

    private boolean matchesNotificationType(String requestType) {
        return matches(requestType, "notification") || matches(requestType, "warning");
    }

    private boolean matchesNotificationFilter(String requestType, TodoNotificationRow item) {
        if (matches(requestType, "notification")) {
            return true;
        }
        return "warning".equalsIgnoreCase(requestType) && isWarningNotification(item);
    }

    private boolean isWarningNotification(TodoNotificationRow item) {
        if (item == null) {
            return false;
        }
        String level = item.getLevel();
        String bizType = item.getBizType();
        return "warning".equalsIgnoreCase(level)
                || "critical".equalsIgnoreCase(level)
                || (bizType != null && bizType.toUpperCase().contains("WARNING"));
    }

    private String resolveNotificationTodoType(TodoNotificationRow item) {
        return isWarningNotification(item) ? "warning" : "notification";
    }

    private String resolveNotificationTag(TodoNotificationRow item) {
        if (item == null) {
            return "提醒";
        }
        if ("INVENTORY_WARNING".equalsIgnoreCase(item.getBizType())) {
            return "库存预警";
        }
        if ("ORDER_STALE_WARNING".equalsIgnoreCase(item.getBizType())) {
            return "订单预警";
        }
        if ("AI_ADVICE".equalsIgnoreCase(item.getBizType())) {
            return "经营建议";
        }
        return isWarningNotification(item) ? "预警" : "提醒";
    }

    private String resolveMiniRoute(TodoNotificationRow item) {
        if (item == null) {
            return "/pages/index/index";
        }
        if ("INVENTORY_WARNING".equalsIgnoreCase(item.getBizType())) {
            return "/pages/inventory/inventory";
        }
        if ("ORDER_STALE_WARNING".equalsIgnoreCase(item.getBizType())) {
            return "/pages/order/order";
        }
        if (item.getRoute() != null && item.getRoute().startsWith("/pages/")) {
            return item.getRoute();
        }
        return "/pages/index/index";
    }

    private boolean hasAnyPermission(String... permCodes) {
        for (String permCode : permCodes) {
            if (TenantPermissionContext.hasPermission(permCode)) {
                return true;
            }
        }
        return false;
    }

    private String currentUserIdText() {
        Long userId = TenantPermissionContext.getUserId();
        return userId == null ? "system" : String.valueOf(userId);
    }

    private String currentTenantCode() {
        String tenantCode = TenantPermissionContext.getTenantCode();
        return tenantCode == null || tenantCode.isBlank() ? null : tenantCode;
    }

    private LocalDateTime firstNotNull(LocalDateTime first, LocalDateTime second) {
        return first != null ? first : second;
    }

    private String firstText(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private long nvl(Long value) {
        return value == null ? 0L : value;
    }

    private long normalize(Long value, long defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private long normalizePageSize(Long value) {
        long pageSize = normalize(value, DEFAULT_PAGE_SIZE);
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private <T> LambdaQueryWrapper<T> withLimit(LambdaQueryWrapper<T> wrapper, Integer limit) {
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + Math.min(limit, MAX_PAGE_SIZE));
        }
        return wrapper;
    }

    private String statusText(String status) {
        if (status == null || status.isBlank()) {
            return "\u672a\u8bbe\u7f6e";
        }
        try {
            return OrderStatusEnum.getByCode(status).getName();
        } catch (IllegalArgumentException ex) {
            return status;
        }
    }

}

package my.hive_back.module.todo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.dto.PageResult;
import my.hive_back.module.badproduct.mapper.BadProductMapper;
import my.hive_back.module.badproduct.model.entity.BadProductRecord;
import my.hive_back.module.finance.mapper.FinanceApprovalMapper;
import my.hive_back.module.finance.model.entity.FinanceApproval;
import my.hive_back.module.inventory.mapper.OutboundOrderMapper;
import my.hive_back.module.inventory.model.entity.OutboundOrder;
import my.hive_back.module.leave.LeaveStatusEnum;
import my.hive_back.module.leave.mapper.LeaveMapper;
import my.hive_back.module.leave.model.entity.UserLeave;
import my.hive_back.module.order.mapper.ProductionOrderMapper;
import my.hive_back.module.order.mapper.SalesOrderMapper;
import my.hive_back.module.order.model.entity.ProductionOrder;
import my.hive_back.module.order.model.entity.SalesOrder;
import my.hive_back.module.todo.model.dto.TodoPageRequest;
import my.hive_back.module.todo.model.vo.TodoItemVO;
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
        String userIdText = currentUserIdText();
        long total = 0L;
        if (userId != null) {
            total += leaveMapper.selectCount(new LambdaQueryWrapper<UserLeave>()
                    .eq(UserLeave::getAuditorId, userId)
                    .eq(UserLeave::getStatus, LeaveStatusEnum.PENDING.getCode()));
            total += financeApprovalMapper.selectCount(new LambdaQueryWrapper<FinanceApproval>()
                    .eq(FinanceApproval::getAuditorId, userId)
                    .eq(FinanceApproval::getStatus, 1));
        }
        if (hasAnyPermission("production:order:list", "production:order:*", "*")) {
            total += productionOrderMapper.selectCount(new LambdaQueryWrapper<ProductionOrder>()
                    .and(wrapper -> wrapper.eq(ProductionOrder::getCreator, userIdText)
                            .or()
                            .eq(ProductionOrder::getUpdater, userIdText))
                    .in(ProductionOrder::getStatus, List.of("pending_confirm", "pending_material", "pending_ship")));
        }
        if (hasAnyPermission("sales:order:list", "sales:order:*", "*")) {
            total += salesOrderMapper.selectCount(new LambdaQueryWrapper<SalesOrder>()
                    .and(wrapper -> wrapper.eq(SalesOrder::getCreator, userIdText)
                            .or()
                            .eq(SalesOrder::getUpdater, userIdText))
                    .in(SalesOrder::getStatus, List.of("pending_confirm", "pending_ship")));
        }
        if (hasAnyPermission("inventory", "inventory:*", "inventory:cloth:out", "*") && userId != null) {
            total += outboundOrderMapper.selectCount(new LambdaQueryWrapper<OutboundOrder>()
                    .eq(OutboundOrder::getOperatorId, userId)
                    .eq(OutboundOrder::getPrintStatus, 0));
        }
        if (hasAnyPermission("badproduct:*", "badproduct:list", "badproduct:process", "*") && userId != null) {
            total += badProductMapper.selectCount(new LambdaQueryWrapper<BadProductRecord>()
                    .eq(BadProductRecord::getCreatorId, userId)
                    .eq(BadProductRecord::getStatus, "pending"));
        }
        return Math.toIntExact(Math.min(total, Integer.MAX_VALUE));
    }

    private List<TodoItemVO> listAll(String type) {
        return listAll(type, null);
    }

    private List<TodoItemVO> listAll(String type, Integer limitPerCategory) {
        Long userId = TenantPermissionContext.getUserId();
        String userIdText = currentUserIdText();
        List<TodoItemVO> todos = new ArrayList<>();

        if (matches(type, "approval") && userId != null) {
            todos.addAll(buildLeaveTodos(userId, limitPerCategory));
            todos.addAll(buildFinanceTodos(userId, limitPerCategory));
        }
        if (matches(type, "order")) {
            todos.addAll(buildProductionTodos(userIdText, limitPerCategory));
            todos.addAll(buildSalesTodos(userIdText, limitPerCategory));
        }
        if (matches(type, "print") && userId != null) {
            todos.addAll(buildOutboundPrintTodos(userId, limitPerCategory));
        }
        if (matches(type, "quality") && userId != null) {
            todos.addAll(buildBadProductTodos(userId, limitPerCategory));
        }

        todos.sort(Comparator.comparing(TodoItemVO::getSortTime).reversed());
        return todos;
    }

    private List<TodoItemVO> buildLeaveTodos(Long userId, Integer limit) {
        List<UserLeave> leaves = leaveMapper.selectList(withLimit(new LambdaQueryWrapper<UserLeave>()
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

    private List<TodoItemVO> buildFinanceTodos(Long userId, Integer limit) {
        List<FinanceApproval> approvals = financeApprovalMapper.selectList(withLimit(new LambdaQueryWrapper<FinanceApproval>()
                .eq(FinanceApproval::getAuditorId, userId)
                .eq(FinanceApproval::getStatus, 1)
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

    private List<TodoItemVO> buildProductionTodos(String userId, Integer limit) {
        if (!hasAnyPermission("production:order:list", "production:order:*", "*")) {
            return List.of();
        }
        List<ProductionOrder> orders = productionOrderMapper.selectList(withLimit(new LambdaQueryWrapper<ProductionOrder>()
                .and(wrapper -> wrapper.eq(ProductionOrder::getCreator, userId)
                        .or()
                        .eq(ProductionOrder::getUpdater, userId))
                .in(ProductionOrder::getStatus, List.of("pending_confirm", "pending_material", "pending_ship"))
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

    private List<TodoItemVO> buildSalesTodos(String userId, Integer limit) {
        if (!hasAnyPermission("sales:order:list", "sales:order:*", "*")) {
            return List.of();
        }
        List<SalesOrder> orders = salesOrderMapper.selectList(withLimit(new LambdaQueryWrapper<SalesOrder>()
                .and(wrapper -> wrapper.eq(SalesOrder::getCreator, userId)
                        .or()
                        .eq(SalesOrder::getUpdater, userId))
                .in(SalesOrder::getStatus, List.of("pending_confirm", "pending_ship"))
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

    private List<TodoItemVO> buildOutboundPrintTodos(Long userId, Integer limit) {
        if (!hasAnyPermission("inventory", "inventory:*", "inventory:cloth:out", "*")) {
            return List.of();
        }
        List<OutboundOrder> orders = outboundOrderMapper.selectList(withLimit(new LambdaQueryWrapper<OutboundOrder>()
                .eq(OutboundOrder::getOperatorId, userId)
                .eq(OutboundOrder::getPrintStatus, 0)
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

    private List<TodoItemVO> buildBadProductTodos(Long userId, Integer limit) {
        if (!hasAnyPermission("badproduct:*", "badproduct:list", "badproduct:process", "*")) {
            return List.of();
        }
        List<BadProductRecord> records = badProductMapper.selectList(withLimit(new LambdaQueryWrapper<BadProductRecord>()
                .eq(BadProductRecord::getCreatorId, userId)
                .eq(BadProductRecord::getStatus, "pending")
                .orderByDesc(BadProductRecord::getCreateTime), limit));

        return records.stream().map(item -> buildTodo(
                "badProduct-" + item.getDefectiveId(),
                "badProduct",
                "次品处理",
                "次品记录待处理",
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

    private LocalDateTime firstNotNull(LocalDateTime first, LocalDateTime second) {
        return first != null ? first : second;
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
        return switch (status == null ? "" : status) {
            case "pending_confirm" -> "待确认";
            case "pending_material" -> "备料中";
            case "producing" -> "生产中";
            case "pending_ship" -> "待发货";
            case "shipped" -> "已发货";
            case "completed" -> "已完成";
            default -> status == null ? "未设置" : status;
        };
    }
}

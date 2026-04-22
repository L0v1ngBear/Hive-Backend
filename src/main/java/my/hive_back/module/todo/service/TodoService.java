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
        long pageSize = normalize(request.getPageSize(), 20L);
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
        return listAll("all").stream().limit(limit).toList();
    }

    public int countAll() {
        return listAll("all").size();
    }

    private List<TodoItemVO> listAll(String type) {
        String tenantCode = TenantPermissionContext.getTenantCode();
        Long userId = TenantPermissionContext.getUserId();
        List<TodoItemVO> todos = new ArrayList<>();

        if (matches(type, "approval")) {
            todos.addAll(buildLeaveTodos(tenantCode, userId));
            todos.addAll(buildFinanceTodos(tenantCode, userId));
        }
        if (matches(type, "order")) {
            todos.addAll(buildProductionTodos(tenantCode, userId));
            todos.addAll(buildSalesTodos(tenantCode, userId));
        }
        if (matches(type, "print")) {
            todos.addAll(buildOutboundPrintTodos(tenantCode, userId));
        }
        if (matches(type, "quality")) {
            todos.addAll(buildBadProductTodos(tenantCode, userId));
        }

        todos.sort(Comparator.comparing(TodoItemVO::getSortTime).reversed());
        return todos;
    }

    private List<TodoItemVO> buildLeaveTodos(String tenantCode, Long userId) {
        List<UserLeave> leaves = leaveMapper.selectList(new LambdaQueryWrapper<UserLeave>()
                .eq(UserLeave::getTenantCode, tenantCode)
                .eq(UserLeave::getAuditorId, userId)
                .eq(UserLeave::getStatus, LeaveStatusEnum.PENDING.getCode())
                .orderByDesc(UserLeave::getCreateTime));

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

    private List<TodoItemVO> buildFinanceTodos(String tenantCode, Long userId) {
        List<FinanceApproval> approvals = financeApprovalMapper.selectList(new LambdaQueryWrapper<FinanceApproval>()
                .eq(FinanceApproval::getTenantCode, tenantCode)
                .eq(FinanceApproval::getAuditorId, userId)
                .eq(FinanceApproval::getStatus, 1)
                .orderByDesc(FinanceApproval::getCreateTime));

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

    private List<TodoItemVO> buildProductionTodos(String tenantCode, Long userId) {
        if (!hasAnyPermission("production:order:list", "production:order:*", "*")) {
            return List.of();
        }
        List<ProductionOrder> orders = productionOrderMapper.selectList(new LambdaQueryWrapper<ProductionOrder>()
                .eq(ProductionOrder::getTenantCode, tenantCode)
                .and(wrapper -> wrapper.eq(ProductionOrder::getCreator, userId)
                        .or()
                        .eq(ProductionOrder::getUpdater, userId))
                .in(ProductionOrder::getStatus, List.of("pending_confirm", "pending_material", "pending_ship"))
                .orderByDesc(ProductionOrder::getUpdateTime));

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

    private List<TodoItemVO> buildSalesTodos(String tenantCode, Long userId) {
        if (!hasAnyPermission("sales:order:list", "sales:order:*", "*")) {
            return List.of();
        }
        List<SalesOrder> orders = salesOrderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getTenantCode, tenantCode)
                .and(wrapper -> wrapper.eq(SalesOrder::getCreator, userId)
                        .or()
                        .eq(SalesOrder::getUpdater, userId))
                .in(SalesOrder::getStatus, List.of("pending_confirm", "pending_ship"))
                .orderByDesc(SalesOrder::getUpdateTime));

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

    private List<TodoItemVO> buildOutboundPrintTodos(String tenantCode, Long userId) {
        if (!hasAnyPermission("inventory", "inventory:*", "inventory:cloth:out", "*")) {
            return List.of();
        }
        List<OutboundOrder> orders = outboundOrderMapper.selectList(new LambdaQueryWrapper<OutboundOrder>()
                .eq(OutboundOrder::getTenantCode, tenantCode)
                .eq(OutboundOrder::getOperatorId, userId)
                .eq(OutboundOrder::getPrintStatus, 0)
                .orderByDesc(OutboundOrder::getCreateTime));

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

    private List<TodoItemVO> buildBadProductTodos(String tenantCode, Long userId) {
        if (!hasAnyPermission("inventory", "inventory:*", "production:order:*", "sales:order:*", "*")) {
            return List.of();
        }
        List<BadProductRecord> records = badProductMapper.selectList(new LambdaQueryWrapper<BadProductRecord>()
                .eq(BadProductRecord::getTenantCode, tenantCode)
                .eq(BadProductRecord::getCreatorId, userId)
                .eq(BadProductRecord::getStatus, "pending")
                .orderByDesc(BadProductRecord::getCreateTime));

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

    private LocalDateTime firstNotNull(LocalDateTime first, LocalDateTime second) {
        return first != null ? first : second;
    }

    private long normalize(Long value, long defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
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

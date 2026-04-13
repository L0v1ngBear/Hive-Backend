package my.hive_back.module.home.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import my.hive_back.common.context.TenantPermissionContext;
import my.hive_back.module.finance.mapper.FinanceApprovalMapper;
import my.hive_back.module.finance.model.entity.FinanceApproval;
import my.hive_back.module.home.model.vo.HomeSummaryVO;
import my.hive_back.module.leave.mapper.LeaveMapper;
import my.hive_back.module.leave.model.entity.UserLeave;
import my.hive_back.module.order.mapper.ProductionOrderMapper;
import my.hive_back.module.order.mapper.SalesOrderMapper;
import my.hive_back.module.order.model.entity.ProductionOrder;
import my.hive_back.module.order.model.entity.SalesOrder;
import my.hive_back.module.tenant.mapper.TenantMapper;
import my.hive_back.module.tenant.model.entity.Tenant;
import my.hive_back.module.user.mapper.UserMapper;
import my.hive_back.module.user.model.entity.User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class HomeService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    @Resource
    private UserMapper userMapper;

    @Resource
    private TenantMapper tenantMapper;

    @Resource
    private LeaveMapper leaveMapper;

    @Resource
    private FinanceApprovalMapper financeApprovalMapper;

    @Resource
    private ProductionOrderMapper productionOrderMapper;

    @Resource
    private SalesOrderMapper salesOrderMapper;

    public HomeSummaryVO getSummary() {
        String tenantCode = TenantPermissionContext.getTenantCode();
        Long userId = TenantPermissionContext.getUserId();

        Tenant tenant = tenantMapper.selectByTenantCode(tenantCode);
        User user = userMapper.selectById(userId);

        HomeSummaryVO vo = new HomeSummaryVO();
        vo.setTenantInfo(buildTenantInfo(tenant));
        vo.setUserInfo(buildUserInfo(user));
        vo.setFunctionEnable(buildFunctionEnable());

        List<HomeSummaryVO.TodoItem> todoItems = buildTodoList(tenantCode, userId);
        vo.setTodoCount(todoItems.size());
        vo.setTodoList(todoItems.stream().limit(6).toList());
        return vo;
    }

    private HomeSummaryVO.TenantInfo buildTenantInfo(Tenant tenant) {
        HomeSummaryVO.TenantInfo tenantInfo = new HomeSummaryVO.TenantInfo();
        if (tenant != null) {
            tenantInfo.setCode(tenant.getTenantCode());
            tenantInfo.setName(tenant.getTenantName());
        }
        return tenantInfo;
    }

    private HomeSummaryVO.UserInfo buildUserInfo(User user) {
        HomeSummaryVO.UserInfo userInfo = new HomeSummaryVO.UserInfo();
        if (user != null) {
            userInfo.setId(user.getId());
            userInfo.setName(user.getName());
            String dept = user.getDepartmentName() == null ? "未分配部门" : user.getDepartmentName();
            String position = user.getPosition() == null ? "未设置岗位" : user.getPosition();
            userInfo.setDept(dept + " - " + position);
        }
        return userInfo;
    }

    private HomeSummaryVO.FunctionEnable buildFunctionEnable() {
        HomeSummaryVO.FunctionEnable functionEnable = new HomeSummaryVO.FunctionEnable();
        functionEnable.setAttendance(hasAnyPermission("attendance", "attendance:*", "attendance:punch", "attendance:record:list"));
        functionEnable.setOrder(hasAnyPermission("production:order", "production:order:*", "production:order:list", "production:order:add", "production:order:detail"));
        functionEnable.setSalesOrder(hasAnyPermission("sales:order", "sales:order:*", "sales:order:list", "sales:order:add", "sales:order:detail"));
        functionEnable.setInventory(hasAnyPermission("inventory", "inventory:*", "inventory:cloth:in", "inventory:cloth:out", "inventory:warning:list"));
        functionEnable.setApproval(hasAnyPermission("approval", "approval:*", "approval:leave", "approval:finance", "approval:leave:submit", "approval:finance:submit"));
        functionEnable.setNotice(false);
        functionEnable.setFile(hasAnyPermission("document", "document:*", "document:list", "document:folder:create"));
        functionEnable.setBadProduct(hasAnyPermission("*", "inventory:*", "inventory", "production:order:*", "sales:order:*"));
        functionEnable.setKnowledge(false);
        functionEnable.setCustomer(hasAnyPermission("customer", "customer:*", "customer:page", "customer:detail", "customer:add"));
        return functionEnable;
    }

    private boolean hasAnyPermission(String... permCodes) {
        for (String permCode : permCodes) {
            if (TenantPermissionContext.hasPermission(permCode)) {
                return true;
            }
        }
        Set<String> currentPerms = TenantPermissionContext.getPermCodes();
        return currentPerms != null && currentPerms.contains("*");
    }

    private List<HomeSummaryVO.TodoItem> buildTodoList(String tenantCode, Long userId) {
        List<HomeSummaryVO.TodoItem> todoItems = new ArrayList<>();
        todoItems.addAll(buildLeaveTodos(tenantCode, userId));
        todoItems.addAll(buildFinanceTodos(tenantCode, userId));
        todoItems.addAll(buildProductionTodos(tenantCode));
        todoItems.addAll(buildSalesTodos(tenantCode));
        todoItems.sort(Comparator.comparing(this::parseTime).reversed());
        return todoItems;
    }

    private List<HomeSummaryVO.TodoItem> buildLeaveTodos(String tenantCode, Long userId) {
        List<UserLeave> leaves = leaveMapper.selectList(new LambdaQueryWrapper<UserLeave>()
                .eq(UserLeave::getTenantCode, tenantCode)
                .eq(UserLeave::getAuditorId, userId)
                .eq(UserLeave::getStatus, 1)
                .orderByDesc(UserLeave::getCreateTime)
                .last("limit 3"));

        return leaves.stream().map(item -> buildTodo(
                item.getLeaveCode(),
                "请假审批",
                "请审批请假单 " + item.getLeaveCode(),
                item.getCreateTime()
        )).toList();
    }

    private List<HomeSummaryVO.TodoItem> buildFinanceTodos(String tenantCode, Long userId) {
        List<FinanceApproval> approvals = financeApprovalMapper.selectList(new LambdaQueryWrapper<FinanceApproval>()
                .eq(FinanceApproval::getTenantCode, tenantCode)
                .eq(FinanceApproval::getAuditorId, userId)
                .eq(FinanceApproval::getStatus, 1)
                .orderByDesc(FinanceApproval::getCreateTime)
                .last("limit 3"));

        return approvals.stream().map(item -> buildTodo(
                item.getApprovalCode(),
                "财务审批",
                "请审批财务单 " + item.getApprovalCode(),
                item.getCreateTime()
        )).toList();
    }

    private List<HomeSummaryVO.TodoItem> buildProductionTodos(String tenantCode) {
        if (!hasAnyPermission("production:order:list", "production:order:*")) {
            return List.of();
        }
        List<ProductionOrder> orders = productionOrderMapper.selectList(new LambdaQueryWrapper<ProductionOrder>()
                .eq(ProductionOrder::getTenantCode, tenantCode)
                .in(ProductionOrder::getStatus, List.of("pending_confirm", "pending_material", "pending_ship"))
                .orderByDesc(ProductionOrder::getUpdateTime)
                .last("limit 3"));

        return orders.stream().map(item -> buildTodo(
                item.getOrderId(),
                "生产订单",
                item.getOrderId() + " 当前状态：" + statusText(item.getStatus()),
                item.getUpdateTime() == null ? item.getCreateTime() : item.getUpdateTime()
        )).toList();
    }

    private List<HomeSummaryVO.TodoItem> buildSalesTodos(String tenantCode) {
        if (!hasAnyPermission("sales:order:list", "sales:order:*")) {
            return List.of();
        }
        List<SalesOrder> orders = salesOrderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getTenantCode, tenantCode)
                .in(SalesOrder::getStatus, List.of("pending_confirm", "pending_ship"))
                .orderByDesc(SalesOrder::getUpdateTime)
                .last("limit 3"));

        return orders.stream().map(item -> buildTodo(
                item.getOrderId(),
                "销售订单",
                item.getOrderId() + " 当前状态：" + statusText(item.getStatus()),
                item.getUpdateTime() == null ? item.getCreateTime() : item.getUpdateTime()
        )).toList();
    }

    private HomeSummaryVO.TodoItem buildTodo(String id, String tag, String content, LocalDateTime time) {
        HomeSummaryVO.TodoItem item = new HomeSummaryVO.TodoItem();
        item.setId(id);
        item.setTag(tag);
        item.setContent(content);
        item.setTime(time == null ? "--" : time.format(TIME_FORMATTER));
        return item;
    }

    private LocalDateTime parseTime(HomeSummaryVO.TodoItem item) {
        try {
            String year = String.valueOf(LocalDateTime.now().getYear());
            return LocalDateTime.parse(year + "-" + item.getTime().replace(" ", "T") + ":00", DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        } catch (Exception ignored) {
            return LocalDateTime.MIN;
        }
    }

    private String statusText(String status) {
        return switch (status) {
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
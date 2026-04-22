package my.hive_back.module.home.service;

import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import my.hive_back.module.home.model.vo.HomeSummaryVO;
import my.hive_back.module.tenant.mapper.TenantMapper;
import my.hive_back.module.tenant.model.entity.Tenant;
import my.hive_back.module.todo.model.vo.TodoItemVO;
import my.hive_back.module.todo.service.TodoService;
import my.hive_back.module.user.mapper.UserMapper;
import my.hive_back.module.user.model.entity.User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
/**
 * HomeService 属于小程序后端首页模块，实现核心业务编排与规则逻辑。
 */
@Service
public class HomeService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private TenantMapper tenantMapper;

    @Resource
    private TodoService todoService;

    public HomeSummaryVO getSummary() {
        String tenantCode = TenantPermissionContext.getTenantCode();
        Long userId = TenantPermissionContext.getUserId();

        Tenant tenant = tenantMapper.selectByTenantCode(tenantCode);
        User user = userMapper.selectById(userId);

        HomeSummaryVO vo = new HomeSummaryVO();
        vo.setTenantInfo(buildTenantInfo(tenant));
        vo.setUserInfo(buildUserInfo(user));
        vo.setFunctionEnable(buildFunctionEnable());

        List<TodoItemVO> todoItems = todoService.listHomeTodos(6);
        vo.setTodoCount(todoService.countAll());
        vo.setTodoList(todoItems);
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

}

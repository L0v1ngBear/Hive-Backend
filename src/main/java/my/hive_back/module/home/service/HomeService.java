package my.hive_back.module.home.service;

import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import my.hive_back.module.home.model.vo.HomeSummaryVO;
import my.hive_back.module.tenant.mapper.TenantMapper;
import my.hive_back.module.tenant.model.entity.Tenant;
import my.hive_back.module.todo.service.TodoService;
import my.hive_back.module.user.mapper.UserMapper;
import my.hive_back.module.user.model.entity.User;
import org.springframework.stereotype.Service;

import java.util.Set;
/**
 * HomeService 属于小程序后端首页模块，实现核心业务编排与规则逻辑。
 */
@Service
public class HomeService {

    private static final String LEGACY_STANDALONE_DEPARTMENT = "未加入组织";
    private static final String LEGACY_STANDALONE_POSITION = "待加入组织";
    private static final String LEGACY_PERMISSION_PLACEHOLDER = "待分配权限";
    private static final String DEFAULT_JOINED_DEPARTMENT = "待分配部门";
    private static final String DEFAULT_JOINED_POSITION = "普通员工";

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
        boolean joinedOrganization = user != null && normalizeTenantCode(user.getTenantCode()) != null;

        HomeSummaryVO vo = new HomeSummaryVO();
        vo.setTenantInfo(joinedOrganization ? buildTenantInfo(tenant) : new HomeSummaryVO.TenantInfo());
        vo.setUserInfo(buildUserInfo(user, joinedOrganization));
        vo.setFunctionEnable(joinedOrganization ? buildFunctionEnable() : emptyFunctionEnable());

        if (!joinedOrganization) {
            vo.setTodoCount(0);
            return vo;
        }

        vo.setTodoCount(todoService.countAll());
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

    private HomeSummaryVO.UserInfo buildUserInfo(User user, boolean joinedOrganization) {
        HomeSummaryVO.UserInfo userInfo = new HomeSummaryVO.UserInfo();
        userInfo.setJoinedOrganization(joinedOrganization);
        if (user != null) {
            userInfo.setId(user.getId());
            userInfo.setName(user.getName());
            userInfo.setDepartmentName(joinedOrganization ? resolveJoinedDepartment(user.getDepartmentName()) : LEGACY_STANDALONE_DEPARTMENT);
            userInfo.setPosition(joinedOrganization ? resolveJoinedPosition(user.getPosition()) : LEGACY_STANDALONE_POSITION);
        }
        return userInfo;
    }

    private String resolveJoinedDepartment(String departmentName) {
        if (departmentName == null || departmentName.isBlank() || LEGACY_STANDALONE_DEPARTMENT.equals(departmentName.trim())) {
            return DEFAULT_JOINED_DEPARTMENT;
        }
        return departmentName.trim();
    }

    private String resolveJoinedPosition(String position) {
        if (position == null
                || position.isBlank()
                || LEGACY_STANDALONE_POSITION.equals(position.trim())
                || LEGACY_PERMISSION_PLACEHOLDER.equals(position.trim())) {
            return DEFAULT_JOINED_POSITION;
        }
        return position.trim();
    }

    private HomeSummaryVO.FunctionEnable buildFunctionEnable() {
        HomeSummaryVO.FunctionEnable functionEnable = new HomeSummaryVO.FunctionEnable();
        functionEnable.setAttendance(hasAnyPermission("attendance", "attendance:*", "attendance:punch", "attendance:record:list"));
        functionEnable.setOrder(hasAnyPermission("production:order", "production:order:*", "production:order:list", "production:order:add", "production:order:detail"));
        functionEnable.setSalesOrder(hasAnyPermission("sales:order", "sales:order:*", "sales:order:list", "sales:order:add", "sales:order:detail"));
        functionEnable.setInventory(hasAnyPermission("inventory", "inventory:*", "inventory:cloth:in", "inventory:cloth:out", "inventory:warning:list"));
        functionEnable.setApproval(hasAnyPermission(
                "approval",
                "approval:*",
                "approval:leave",
                "approval:finance",
                "approval:resignation",
                "approval:leave:submit",
                "approval:finance:submit",
                "approval:resignation:submit",
                "sales:order:list",
                "production:order:list"
        ));
        functionEnable.setNotice(false);
        functionEnable.setFile(hasAnyPermission("document", "document:*", "document:list", "document:folder:create"));
        functionEnable.setBadProduct(hasAnyPermission("*", "badproduct:*", "badproduct:list", "badproduct:save", "badproduct:process"));
        functionEnable.setKnowledge(false);
        functionEnable.setCustomer(hasAnyPermission("customer", "customer:*", "customer:page", "customer:detail", "customer:add"));
        functionEnable.setDocument(hasAnyPermission("document", "document:*", "document:list", "document:folder:create", "document:file:upload"));
        functionEnable.setLabelTemplate(hasAnyPermission("label", "label:*", "label:template:list", "label:template:detail", "label:template:default"));
        functionEnable.setEquipmentInspection(hasAnyPermission("equipment", "equipment:*", "equipment:list", "equipment:inspection:submit"));
        return functionEnable;
    }

    private HomeSummaryVO.FunctionEnable emptyFunctionEnable() {
        HomeSummaryVO.FunctionEnable functionEnable = new HomeSummaryVO.FunctionEnable();
        functionEnable.setAttendance(false);
        functionEnable.setOrder(false);
        functionEnable.setSalesOrder(false);
        functionEnable.setInventory(false);
        functionEnable.setApproval(false);
        functionEnable.setNotice(false);
        functionEnable.setFile(false);
        functionEnable.setBadProduct(false);
        functionEnable.setKnowledge(false);
        functionEnable.setCustomer(false);
        functionEnable.setDocument(false);
        functionEnable.setLabelTemplate(false);
        functionEnable.setEquipmentInspection(false);
        return functionEnable;
    }

    private boolean hasAnyPermission(String... permCodes) {
        for (String permCode : permCodes) {
            if (TenantPermissionContext.hasPermission(permCode)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeTenantCode(String tenantCode) {
        if (tenantCode == null || tenantCode.trim().isEmpty()) {
            return null;
        }
        return tenantCode.trim();
    }

}

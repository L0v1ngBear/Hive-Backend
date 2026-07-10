package my.hive_back.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive.common.redis.HiveRedisKeyBuilder;
import my.hive_back.module.sys.model.entity.SysPermission;
import my.hive_back.module.sys.model.entity.SysRole;
import my.hive_back.module.sys.model.entity.SysUserRole;
import my.hive_back.module.sys.model.enums.PermissionCodeEnum;
import my.hive_back.module.sys.model.mapper.SysPermissionMapper;
import my.hive_back.module.sys.model.mapper.SysRoleMapper;
import my.hive_back.module.sys.model.mapper.SysRolePermissionMapper;
import my.hive_back.module.sys.model.mapper.SysUserRoleMapper;
import my.hive_back.module.user.UserStatusEnum;
import my.hive_back.module.user.mapper.UserMapper;
import my.hive_back.module.user.model.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 小程序用户服务。
 */
@Service
public class UserService {

    private static final int MAX_LOGIN_NAME_LENGTH = 64;
    private static final String LEGACY_STANDALONE_DEPARTMENT = "未加入组织";
    private static final String LEGACY_STANDALONE_POSITION = "待加入组织";
    private static final String LEGACY_PERMISSION_PLACEHOLDER = "待分配权限";
    private static final String DEFAULT_JOINED_DEPARTMENT = "待分配部门";
    private static final String DEFAULT_JOIN_ROLE_CODE = "EMPLOYEE";
    private static final String DEFAULT_JOIN_ROLE_NAME = "普通员工";
    private static final List<String> DEFAULT_JOIN_PERMISSION_CODES = List.of(
            PermissionCodeEnum.CODE_ATTENDANCE_PUNCH,
            PermissionCodeEnum.CODE_ATTENDANCE_RECORD_LIST,
            PermissionCodeEnum.CODE_APPROVAL_LEAVE_SUBMIT,
            PermissionCodeEnum.CODE_APPROVAL_LEAVE_DETAIL,
            PermissionCodeEnum.CODE_APPROVAL_FINANCE_SUBMIT,
            PermissionCodeEnum.CODE_APPROVAL_FINANCE_DETAIL,
            PermissionCodeEnum.CODE_APPROVAL_RESIGNATION_SUBMIT,
            PermissionCodeEnum.CODE_APPROVAL_RESIGNATION_DETAIL,
            PermissionCodeEnum.CODE_DOCUMENT_LIST,
            PermissionCodeEnum.CODE_DOCUMENT_BREADCRUMBS,
            PermissionCodeEnum.CODE_NOTIFICATION_ANNOUNCEMENT_LIST
    );

    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private HiveRedisKeyBuilder redisKeyBuilder;

    @Resource
    private SysRoleMapper sysRoleMapper;

    @Resource
    private SysPermissionMapper sysPermissionMapper;

    @Resource
    private SysRolePermissionMapper sysRolePermissionMapper;

    @Resource
    private SysUserRoleMapper sysUserRoleMapper;

    public Long getManagerId(Long applyUserId) {
        if (applyUserId == null) {
            return null;
        }
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getId, applyUserId);
        queryWrapper.select(User::getManagerId);
        User user = userMapper.selectOne(queryWrapper);
        return user == null ? null : user.getManagerId();
    }

    public User getUserById(Long applyUserId) {
        return userMapper.selectById(applyUserId);
    }

    public Integer getRoleLevel(Long userId) {
        if (userId == null) {
            return null;
        }
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getId, userId);
        queryWrapper.select(User::getRoleLevel);
        User user = userMapper.selectOne(queryWrapper);
        return user == null ? null : user.getRoleLevel();
    }

    @Transactional(rollbackFor = Exception.class)
    public void markResignedByApproval(Long userId) {
        String tenantCode = TenantPermissionContext.getTenantCode();
        if (userId == null || tenantCode == null || tenantCode.isBlank()) {
            throw new BusinessException("离职员工信息异常");
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, userId)
                .eq(User::getTenantCode, tenantCode)
                .last("LIMIT 1"));
        if (user == null) {
            throw new BusinessException("员工不存在");
        }
        if (!UserStatusEnum.isResigned(user.getStatus())) {
            user.setStatus(UserStatusEnum.RESIGNED.getCode());
            user.setUpdateTime(LocalDateTime.now());
            userMapper.updateById(user);
        }

        List<SysUserRole> activeRoles = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getTenantCode, tenantCode)
                .eq(SysUserRole::getIsDeleted, 0));
        for (SysUserRole role : activeRoles) {
            role.setIsDeleted(1);
            sysUserRoleMapper.updateById(role);
        }
        clearUserPermissionCache(userId, tenantCode);
    }

    @Transactional(rollbackFor = Exception.class)
    public User ensureSingleTenantMembership(User user, String tenantCode, String fallbackName) {
        String safeTenantCode = normalizeTenantCode(tenantCode);
        if (user == null || user.getId() == null || safeTenantCode == null) {
            throw new BusinessException(401, "登录状态异常，请重新登录");
        }

        String currentTenantCode = normalizeTenantCode(user.getTenantCode());
        if (currentTenantCode != null && !safeTenantCode.equals(currentTenantCode)) {
            throw new BusinessException(409, "该账号不属于当前系统组织，请联系管理员确认员工归属");
        }

        boolean changed = false;
        if (currentTenantCode == null) {
            user.setTenantCode(safeTenantCode);
            changed = true;
        }
        String safeName = defaultText(fallbackName, "微信用户").trim();
        if ((user.getName() == null || user.getName().isBlank()) && !safeName.isBlank()) {
            user.setName(safeName);
            changed = true;
        }
        String departmentName = resolveJoinedDepartment(user.getDepartmentName());
        if (!departmentName.equals(user.getDepartmentName())) {
            user.setDepartmentName(departmentName);
            changed = true;
        }
        String position = resolveJoinedPosition(user.getPosition());
        if (!position.equals(user.getPosition())) {
            user.setPosition(position);
            changed = true;
        }
        String loginName = resolveTenantLoginName(safeTenantCode, user);
        if (user.getLoginName() == null || user.getLoginName().isBlank() || !user.getLoginName().equals(loginName)) {
            user.setLoginName(loginName);
            changed = true;
        }
        if (user.getRoleLevel() == null) {
            user.setRoleLevel(0);
            changed = true;
        }
        if (changed) {
            user.setUpdateTime(LocalDateTime.now());
            userMapper.updateById(user);
        }
        grantDefaultRoleIfAbsent(user.getId(), safeTenantCode);
        return user;
    }

    private void grantDefaultRoleIfAbsent(Long userId, String tenantCode) {
        if (userId == null || tenantCode == null || tenantCode.isBlank()) {
            return;
        }
        SysRole defaultRole = ensureDefaultJoinRole(tenantCode);
        ensureDefaultRolePermissions(defaultRole.getId());

        long activeRoleCount = sysUserRoleMapper.countActiveRolesByUserIdAndTenantCode(userId, tenantCode);
        if (activeRoleCount > 0) {
            clearUserPermissionCache(userId, tenantCode);
            return;
        }

        sysUserRoleMapper.insertIfAbsent(userId, tenantCode, defaultRole.getId());
        clearUserPermissionCache(userId, tenantCode);
    }

    private SysRole ensureDefaultJoinRole(String tenantCode) {
        SysRole role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantCode, tenantCode)
                .eq(SysRole::getRoleCode, DEFAULT_JOIN_ROLE_CODE)
                .eq(SysRole::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (role != null) {
            return role;
        }

        role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantCode, tenantCode)
                .eq(SysRole::getRoleName, DEFAULT_JOIN_ROLE_NAME)
                .eq(SysRole::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (role != null) {
            return role;
        }

        SysRole defaultRole = new SysRole();
        defaultRole.setTenantCode(tenantCode);
        defaultRole.setRoleCode(DEFAULT_JOIN_ROLE_CODE);
        defaultRole.setRoleName(DEFAULT_JOIN_ROLE_NAME);
        defaultRole.setIsSystem(1);
        defaultRole.setIsDeleted(0);
        defaultRole.setCreateTime(LocalDateTime.now());
        defaultRole.setUpdateTime(LocalDateTime.now());
        sysRoleMapper.insert(defaultRole);
        return defaultRole;
    }

    private void ensureDefaultRolePermissions(Long roleId) {
        if (roleId == null) {
            throw new BusinessException("默认角色配置异常，请联系管理员");
        }
        List<SysPermission> permissions = sysPermissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .in(SysPermission::getPermCode, DEFAULT_JOIN_PERMISSION_CODES)
                .eq(SysPermission::getIsDeleted, 0));
        if (permissions == null || permissions.isEmpty()) {
            throw new BusinessException("系统默认权限未初始化，请联系管理员");
        }
        for (SysPermission permission : permissions) {
            if (permission != null && permission.getId() != null) {
                sysRolePermissionMapper.insertIfAbsent(roleId, permission.getId());
            }
        }
    }

    private void clearUserPermissionCache(Long userId, String tenantCode) {
        if (userId == null || tenantCode == null || tenantCode.isBlank()) {
            return;
        }
        try {
            stringRedisTemplate.delete(redisKeyBuilder.cache("mini", "perm-v2", tenantCode, String.valueOf(userId)));
        } catch (Exception ignored) {
            // Permission cache is an acceleration layer only; role binding has already been persisted.
        }
    }

    private String resolveTenantLoginName(String tenantCode, User user) {
        String rawLoginName = user.getLoginName();
        String base = rawLoginName == null || rawLoginName.isBlank()
                ? "wx_" + user.getId()
                : rawLoginName.trim();
        if (base.length() > MAX_LOGIN_NAME_LENGTH) {
            base = base.substring(0, MAX_LOGIN_NAME_LENGTH);
        }

        User sameLoginNameUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getTenantCode, tenantCode)
                .eq(User::getLoginName, base)
                .ne(User::getId, user.getId())
                .last("LIMIT 1"));
        if (sameLoginNameUser == null) {
            return base;
        }

        String suffix = "_" + user.getId();
        int maxBaseLength = Math.max(1, MAX_LOGIN_NAME_LENGTH - suffix.length());
        return base.substring(0, Math.min(base.length(), maxBaseLength)) + suffix;
    }

    private String normalizeTenantCode(String tenantCode) {
        if (tenantCode == null || tenantCode.trim().isEmpty()) {
            return null;
        }
        return tenantCode.trim();
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
            return DEFAULT_JOIN_ROLE_NAME;
        }
        return position.trim();
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

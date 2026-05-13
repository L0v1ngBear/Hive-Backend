package my.hive_back.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive.common.privacy.PrivacyProtectionUtil;
import my.hive.common.redis.HiveRedisKeyBuilder;
import my.hive.common.utils.ResponseEncryptUtil;
import my.hive.common.utils.TokenUtil;
import my.hive_back.module.auth.model.vo.LoginVO;
import my.hive_back.module.sys.model.entity.SysPermission;
import my.hive_back.module.sys.model.entity.SysRole;
import my.hive_back.module.sys.model.enums.PermissionCodeEnum;
import my.hive_back.module.sys.model.mapper.SysPermissionMapper;
import my.hive_back.module.sys.model.mapper.SysRoleMapper;
import my.hive_back.module.sys.model.mapper.SysRolePermissionMapper;
import my.hive_back.module.sys.model.mapper.SysUserRoleMapper;
import my.hive_back.module.tenant.mapper.TenantMapper;
import my.hive_back.module.tenant.model.entity.Tenant;
import my.hive_back.module.user.UserStatusEnum;
import my.hive_back.module.user.mapper.UserMapper;
import my.hive_back.module.user.model.dto.JoinOrganizationRequest;
import my.hive_back.module.user.model.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 小程序用户服务。
 */
@Service
public class UserService {

    private static final int MAX_LOGIN_NAME_LENGTH = 64;
    private static final int MAX_JOIN_CODE_LENGTH = 12;
    private static final String DEFAULT_JOIN_ROLE_CODE = "EMPLOYEE";
    private static final String DEFAULT_JOIN_ROLE_NAME = "普通员工";
    private static final TypeReference<Map<String, Object>> JOIN_CODE_PAYLOAD_TYPE = new TypeReference<>() {
    };
    private static final List<String> DEFAULT_JOIN_PERMISSION_CODES = List.of(
            PermissionCodeEnum.CODE_ATTENDANCE_PUNCH,
            PermissionCodeEnum.CODE_ATTENDANCE_RECORD_LIST,
            PermissionCodeEnum.CODE_APPROVAL_LEAVE_SUBMIT,
            PermissionCodeEnum.CODE_APPROVAL_LEAVE_DETAIL
    );

    @Resource
    private UserMapper userMapper;

    @Resource
    private TenantMapper tenantMapper;

    @Resource
    private PrivacyProtectionUtil privacyProtectionUtil;

    @Resource
    private ResponseEncryptUtil responseEncryptUtil;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

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

    @Value("${auth.token.expire-hours:24}")
    private Long tokenExpireHours;

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
    public LoginVO joinOrganization(JoinOrganizationRequest request) {
        Long currentUserId = TenantPermissionContext.getUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "请先登录");
        }

        String tenantCode = resolveTenantCodeByJoinCode(request == null ? null : request.getJoinCode());

        Tenant tenant = tenantMapper.selectByTenantCode(tenantCode);
        if (!isTenantUsable(tenant)) {
            throw new BusinessException(404, "组织不存在或暂不可用");
        }

        User currentUser = userMapper.selectById(currentUserId);
        if (currentUser == null || !UserStatusEnum.isUsable(currentUser.getStatus())) {
            throw new BusinessException(401, "登录已失效");
        }

        String currentTenantCode = normalizeTenantCode(currentUser.getTenantCode());
        if (currentTenantCode != null) {
            if (currentTenantCode.equals(tenantCode)) {
                grantDefaultRoleIfAbsent(currentUser.getId(), tenantCode);
                return buildLoginVO(currentUser, tenant);
            }
            throw new BusinessException(409, "当前账号已加入其它组织，请退出后使用对应组织账号");
        }
        if (currentUser.getPhoneHash() == null || currentUser.getPhoneHash().isBlank()) {
            throw new BusinessException("当前账号缺少手机号信息，请重新微信一键登录");
        }

        User existingTenantUser = resolveExistingUserForJoin(currentUser, tenantCode);
        if (existingTenantUser != null) {
            grantDefaultRoleIfAbsent(existingTenantUser.getId(), tenantCode);
            return buildLoginVO(existingTenantUser, tenant);
        }

        currentUser.setTenantCode(tenantCode);
        currentUser.setDepartmentName(defaultText(currentUser.getDepartmentName(), "待分配部门"));
        currentUser.setPosition(DEFAULT_JOIN_ROLE_NAME);
        currentUser.setRoleLevel(0);
        currentUser.setLoginName(resolveTenantLoginName(tenantCode, currentUser));
        currentUser.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(currentUser);
        grantDefaultRoleIfAbsent(currentUser.getId(), tenantCode);

        return buildLoginVO(currentUser, tenant);
    }

    private String resolveTenantCodeByJoinCode(String rawJoinCode) {
        String joinCode = normalizeJoinCode(rawJoinCode);
        if (joinCode == null) {
            throw new BusinessException("请输入组织邀请码");
        }
        if (!joinCode.matches("^[A-Z0-9]{6," + MAX_JOIN_CODE_LENGTH + "}$")) {
            throw new BusinessException("组织邀请码格式不正确");
        }

        String payloadJson;
        try {
            payloadJson = stringRedisTemplate.opsForValue().get(joinCodeKey(joinCode));
        } catch (Exception e) {
            throw new BusinessException("组织邀请码服务暂不可用，请稍后重试");
        }
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new BusinessException(404, "组织邀请码无效或已过期，请联系管理员重新生成");
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(payloadJson, JOIN_CODE_PAYLOAD_TYPE);
            Object tenantValue = payload == null ? null : payload.get("tenantCode");
            String tenantCode = tenantValue instanceof String ? normalizeTenantCode((String) tenantValue) : null;
            if (tenantCode == null) {
                throw new BusinessException("组织邀请码数据异常，请联系管理员重新生成");
            }
            return tenantCode;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("组织邀请码数据异常，请联系管理员重新生成");
        }
    }

    private void grantDefaultRoleIfAbsent(Long userId, String tenantCode) {
        if (userId == null || tenantCode == null || tenantCode.isBlank()) {
            return;
        }
        long activeRoleCount = sysUserRoleMapper.countActiveRolesByUserIdAndTenantCode(userId, tenantCode);
        if (activeRoleCount > 0) {
            return;
        }

        SysRole defaultRole = ensureDefaultJoinRole(tenantCode);
        ensureDefaultRolePermissions(defaultRole.getId());
        sysUserRoleMapper.insertIfAbsent(userId, tenantCode, defaultRole.getId());
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

    private LoginVO buildLoginVO(User user, Tenant tenant) {
        String tenantCode = tenant.getTenantCode();
        String token = TokenUtil.createToken(user.getId(), tenantCode);

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setExpireAt(Instant.now().plus(Duration.ofHours(tokenExpireHours)).getEpochSecond());
        vo.setUserId(user.getId());
        vo.setUserName(defaultText(user.getName(), "微信用户"));
        vo.setPhone(privacyProtectionUtil.displayPhone(user.getPhone(), user.getPhoneMask()));
        vo.setPosition(user.getPosition());
        vo.setTenantCode(tenantCode);
        vo.setTenantName(tenant.getTenantName());
        vo.setNeedsOrganization(false);
        vo.setResponseKey(responseEncryptUtil.buildResponseKey(token));
        return vo;
    }

    private User resolveExistingUserForJoin(User currentUser, String tenantCode) {
        List<User> samePhoneUsers = userMapper.selectList(new LambdaQueryWrapper<User>()
                .and(wrapper -> {
                    wrapper.eq(User::getPhoneHash, currentUser.getPhoneHash());
                    if (currentUser.getPhone() != null && !currentUser.getPhone().isBlank()) {
                        wrapper.or().eq(User::getPhone, currentUser.getPhone());
                    }
                })
                .last("LIMIT 20"));
        if (samePhoneUsers == null || samePhoneUsers.isEmpty()) {
            return null;
        }

        List<User> tenantUsers = samePhoneUsers.stream()
                .filter(user -> user != null && normalizeTenantCode(user.getTenantCode()) != null)
                .toList();
        for (User user : tenantUsers) {
            String userTenantCode = normalizeTenantCode(user.getTenantCode());
            if (!tenantCode.equals(userTenantCode) && UserStatusEnum.isUsable(user.getStatus())) {
                throw new BusinessException(409, "该手机号已加入其它组织，请联系管理员确认员工归属");
            }
            if (tenantCode.equals(userTenantCode) && UserStatusEnum.isResigned(user.getStatus())) {
                throw new BusinessException(403, "该手机号在当前组织已离职，请联系管理员重新启用");
            }
        }

        List<User> usableSameTenantUsers = tenantUsers.stream()
                .filter(user -> tenantCode.equals(normalizeTenantCode(user.getTenantCode())))
                .filter(user -> UserStatusEnum.isUsable(user.getStatus()))
                .toList();
        if (usableSameTenantUsers.size() > 1) {
            throw new BusinessException(409, "该手机号在当前组织存在多个账号，请联系管理员清理员工数据");
        }
        return usableSameTenantUsers.isEmpty() ? null : usableSameTenantUsers.get(0);
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

    private boolean isTenantUsable(Tenant tenant) {
        if (tenant == null || Objects.equals(tenant.getDeleted(), 1) || !Objects.equals(tenant.getStatus(), 1)) {
            return false;
        }
        String subscriptionStatus = tenant.getSubscriptionStatus();
        if (subscriptionStatus != null && !subscriptionStatus.isBlank()) {
            String normalized = subscriptionStatus.trim().toUpperCase(Locale.ROOT);
            if ("EXPIRED".equals(normalized) || "SUSPENDED".equals(normalized)) {
                return false;
            }
        }
        LocalDateTime endTime = tenant.getSubscriptionEndTime();
        return endTime == null || !endTime.isBefore(LocalDateTime.now());
    }

    private String normalizeTenantCode(String tenantCode) {
        if (tenantCode == null || tenantCode.trim().isEmpty()) {
            return null;
        }
        return tenantCode.trim();
    }

    private String normalizeJoinCode(String joinCode) {
        if (joinCode == null || joinCode.trim().isEmpty()) {
            return null;
        }
        return joinCode.trim().toUpperCase(Locale.ROOT);
    }

    private String joinCodeKey(String joinCode) {
        return redisKeyBuilder.cache("tenant", "join-code", joinCode);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

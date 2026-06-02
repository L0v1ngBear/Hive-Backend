package my.hive_back.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive.common.privacy.PrivacyProtectionUtil;
import my.hive.common.redis.HiveRedisKeyBuilder;
import my.hive.common.utils.EncryptUtil;
import my.hive.common.utils.ResponseEncryptUtil;
import my.hive.common.utils.TokenUtil;
import my.hive_back.common.enums.CommonStatusEnum;
import my.hive_back.common.tenant.BoundedTenantProperties;
import my.hive_back.module.auth.model.dto.JoinOrganizationRequest;
import my.hive_back.module.auth.model.dto.LoginRequest;
import my.hive_back.module.auth.model.dto.WechatLoginRequest;
import my.hive_back.module.auth.model.vo.LoginVO;
import my.hive_back.module.tenant.mapper.TenantMapper;
import my.hive_back.module.tenant.model.entity.Tenant;
import my.hive_back.module.user.UserStatusEnum;
import my.hive_back.module.user.mapper.UserMapper;
import my.hive_back.module.user.model.entity.User;
import my.hive_back.module.user.service.UserService;
import my.hive_back.module.wechat.model.vo.WechatPhoneInfoVO;
import my.hive_back.module.wechat.service.WechatMiniProgramClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 小程序认证服务。
 */
@Service
public class AuthService {

    private static final String LEGACY_STANDALONE_DEPARTMENT = "未加入组织";
    private static final String LEGACY_STANDALONE_POSITION = "待加入组织";
    private static final String DEFAULT_JOINED_DEPARTMENT = "待分配部门";
    private static final String LEGACY_PERMISSION_PLACEHOLDER = "待分配权限";
    private static final String DEFAULT_JOINED_POSITION = "普通员工";
    private static final String JOIN_CODE_KEY_PART = "organization-join-code";

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserService userService;

    @Resource
    private TenantMapper tenantMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private EncryptUtil encryptUtil;

    @Resource
    private ResponseEncryptUtil responseEncryptUtil;

    @Resource
    private PrivacyProtectionUtil privacyProtectionUtil;

    @Resource
    private WechatMiniProgramClient wechatMiniProgramClient;

    @Resource
    private HiveRedisKeyBuilder redisKeyBuilder;

    @Resource
    private BoundedTenantProperties boundedTenantProperties;

    @Value("${auth.login.max-fail-count:5}")
    private Long maxFailCount;

    @Value("${auth.login.lock-minutes:15}")
    private Long lockMinutes;

    @Value("${auth.login.max-ip-fail-count:20}")
    private Long maxIpFailCount;

    @Value("${auth.token.expire-hours:24}")
    private Long tokenExpireHours;

    @Value("${hive.single-tenant.name:当前组织}")
    private String singleTenantName;

    public LoginVO login(LoginRequest request, String clientIp) {
        String username = request.getUsername().trim();
        String phoneHash = privacyProtectionUtil.mayBePhoneKeyword(username) ? privacyProtectionUtil.hashPhone(username) : null;
        String safeClientIp = normalizeClientIp(clientIp);
        String accountFailKey = redisKeyBuilder.counter("auth", "mini-login", "fail", "account",
                accountFailKeySegment(username, phoneHash));
        String ipFailKey = redisKeyBuilder.counter("auth", "mini-login", "fail", "ip", safeClientIp);

        ensureLoginNotLocked(accountFailKey, maxFailCount, "登录失败次数过多，请稍后再试");
        ensureLoginNotLocked(ipFailKey, maxIpFailCount, "当前访问过于频繁，请稍后再试");

        List<User> matchedUsers = userMapper.selectList(new LambdaQueryWrapper<User>()
                .and(wrapper -> {
                    wrapper.eq(User::getLoginName, username);
                    if (phoneHash != null && !phoneHash.isBlank()) {
                        wrapper.or().eq(User::getPhoneHash, phoneHash);
                    }
                    wrapper.or().eq(User::getPhone, username);
                })
                .last("LIMIT 20"));

        User user = resolveAccountLoginUser(matchedUsers);
        if (user == null || !UserStatusEnum.isUsable(user.getStatus()) || !encryptUtil.matches(request.getPassword(), user.getPassword())) {
            recordLoginFail(accountFailKey);
            recordLoginFail(ipFailKey);
            throw new BusinessException(401, "账号或密码错误");
        }

        if (normalizeTenantCode(user.getTenantCode()) == null) {
            stringRedisTemplate.delete(accountFailKey);
            stringRedisTemplate.delete(ipFailKey);
            return buildUnjoinedLoginVO(user);
        }

        String tenantCode = resolveLoginTenantCode(user);
        user = userService.ensureSingleTenantMembership(user, tenantCode, user.getName());
        Tenant tenant = tenantMapper.selectByTenantCode(tenantCode);
        if (tenant == null || !CommonStatusEnum.ENABLED.matches(tenant.getStatus())) {
            recordLoginFail(accountFailKey);
            recordLoginFail(ipFailKey);
            throw new BusinessException(403, "组织不可用");
        }
        if (!encryptUtil.isBcryptHash(user.getPassword())) {
            user.setPassword(encryptUtil.encode(request.getPassword()));
            userMapper.updateById(user);
        }

        stringRedisTemplate.delete(accountFailKey);
        stringRedisTemplate.delete(ipFailKey);

        String token = TokenUtil.createToken(user.getId(), tenantCode);
        return buildLoginVO(user, token, tenantCode);
    }

    private User resolveAccountLoginUser(List<User> users) {
        if (users == null || users.isEmpty()) {
            return null;
        }
        List<User> usableUsers = users.stream()
                .filter(user -> user != null && UserStatusEnum.isUsable(user.getStatus()))
                .toList();
        if (usableUsers.isEmpty()) {
            throw new BusinessException(403, "该账号已停用或离职，请联系管理员");
        }
        List<User> tenantUsers = usableUsers.stream()
                .filter(this::isAllowedTenantUser)
                .sorted(Comparator.comparing(User::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (tenantUsers.size() == 1) {
            return tenantUsers.get(0);
        }
        if (tenantUsers.size() > 1) {
            throw new BusinessException(409, "该账号存在多个组织账号，请联系管理员清理员工数据");
        }
        return usableUsers.stream()
                .filter(user -> normalizeTenantCode(user.getTenantCode()) == null)
                .findFirst()
                .orElse(null);
    }

    /**
     * 微信手机号一键登录。
     *
     * 新手机号先创建登录身份；必须输入组织码和姓名后才会绑定组织和默认普通员工权限。
     */
    public LoginVO wechatLogin(WechatLoginRequest request) {
        if (request == null || !hasText(request.getPhoneCode())) {
            throw new BusinessException("缺少微信手机号授权码，请重新授权后登录");
        }
        WechatPhoneInfoVO phoneInfo = wechatMiniProgramClient.getPhoneNumber(request.getPhoneCode().trim());
        String normalizedPhone = privacyProtectionUtil.normalizePhone(
                phoneInfo.getPurePhoneNumber() != null && !phoneInfo.getPurePhoneNumber().isBlank()
                        ? phoneInfo.getPurePhoneNumber()
                        : phoneInfo.getPhoneNumber());
        if (normalizedPhone == null) {
            throw new BusinessException("微信手机号格式异常");
        }

        String phoneHash = privacyProtectionUtil.hashPhone(normalizedPhone);
        User user = resolveWechatLoginUser(phoneHash, normalizedPhone);
        if (normalizeTenantCode(user.getTenantCode()) == null) {
            return buildUnjoinedLoginVO(user);
        }

        String tenantCode = resolveLoginTenantCode(user);
        user = userService.ensureSingleTenantMembership(user, tenantCode, "微信用户");
        Tenant tenant = tenantMapper.selectByTenantCode(tenantCode);
        if (tenant == null || !CommonStatusEnum.ENABLED.matches(tenant.getStatus())) {
            throw new BusinessException(403, "组织不可用");
        }

        String token = TokenUtil.createToken(user.getId(), tenantCode);
        return buildLoginVO(user, token, tenantCode);
    }

    public LoginVO currentUser() {
        Long userId = TenantPermissionContext.getUserId();
        String contextTenantCode = normalizeTenantCode(TenantPermissionContext.getTenantCode());
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "登录已失效");
        }

        String storedTenantCode = normalizeTenantCode(user.getTenantCode());
        if (storedTenantCode == null) {
            return buildUnjoinedLoginVO(user);
        }

        String resolvedTenantCode = contextTenantCode != null ? contextTenantCode : storedTenantCode;
        if (!storedTenantCode.equals(resolvedTenantCode)) {
            throw new BusinessException(409, "当前登录组织与账号组织不一致，请重新登录");
        }
        user = userService.ensureSingleTenantMembership(user, resolvedTenantCode, user.getName());
        Tenant tenant = tenantMapper.selectByTenantCode(resolvedTenantCode);
        if (tenant == null || !CommonStatusEnum.ENABLED.matches(tenant.getStatus())) {
            throw new BusinessException(403, "组织不可用");
        }
        String token = TokenUtil.createToken(user.getId(), resolvedTenantCode);
        return buildLoginVO(user, token, resolvedTenantCode);
    }

    public LoginVO joinOrganization(JoinOrganizationRequest request) {
        Long userId = TenantPermissionContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录后再加入组织");
        }

        String safeName = normalizeDisplayName(request.getName());
        String tenantCode = resolveTenantCodeByOrganizationCode(request.getOrganizationCode());
        Tenant tenant = tenantMapper.selectByTenantCode(tenantCode);
        if (tenant == null || !CommonStatusEnum.ENABLED.matches(tenant.getStatus())) {
            throw new BusinessException(404, "组织码无效或已过期");
        }

        User user = userMapper.selectById(userId);
        if (user == null || !UserStatusEnum.isUsable(user.getStatus())) {
            throw new BusinessException(403, "当前账号不可用，请联系管理员");
        }
        String currentTenantCode = normalizeTenantCode(user.getTenantCode());
        if (currentTenantCode != null && !currentTenantCode.equals(tenantCode)) {
            throw new BusinessException(409, "该手机号已加入其他组织，请先由原组织办理离职或解绑");
        }

        user.setName(safeName);
        user = userService.ensureSingleTenantMembership(user, tenantCode, safeName);
        String token = TokenUtil.createToken(user.getId(), tenantCode);
        return buildLoginVO(user, token, tenantCode);
    }

    private User resolveWechatLoginUser(String phoneHash, String normalizedPhone) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .and(query -> query.eq(User::getPhoneHash, phoneHash)
                        .or()
                        .eq(User::getPhone, normalizedPhone));
        List<User> users = userMapper.selectList(wrapper.last("LIMIT 20"));
        if (users == null || users.isEmpty()) {
            return createStandaloneWechatUser(phoneHash, normalizedPhone);
        }

        List<User> usableUsers = users.stream()
                .filter(user -> user != null && UserStatusEnum.isUsable(user.getStatus()))
                .toList();
        if (usableUsers.isEmpty()) {
            throw new BusinessException(403, "该手机号对应员工账号已离职，请联系管理员重新启用后再登录");
        }

        List<User> tenantUsers = usableUsers.stream()
                .filter(this::isAllowedTenantUser)
                .sorted(Comparator.comparing(User::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (tenantUsers.size() == 1) {
            return tenantUsers.get(0);
        }
        if (tenantUsers.size() > 1) {
            throw new BusinessException(409, "该手机号存在多个组织账号，请联系管理员清理员工数据");
        }

        Optional<User> standaloneUser = usableUsers.stream()
                .filter(user -> normalizeTenantCode(user.getTenantCode()) == null)
                .findFirst();
        return standaloneUser.orElseGet(() -> createStandaloneWechatUser(phoneHash, normalizedPhone));
    }

    private User createStandaloneWechatUser(String phoneHash, String normalizedPhone) {
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhoneHash, phoneHash)
                .isNull(User::getTenantCode)
                .eq(User::getStatus, UserStatusEnum.ACTIVE.getCode())
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }

        User user = new User();
        user.setName("微信用户");
        user.setLoginName(buildStandaloneLoginName(phoneHash));
        user.setPassword(null);
        user.setPhone(null);
        user.setPhoneHash(phoneHash);
        user.setPhoneMask(privacyProtectionUtil.maskPhone(normalizedPhone));
        user.setDepartmentName(LEGACY_STANDALONE_DEPARTMENT);
        user.setPosition(LEGACY_STANDALONE_POSITION);
        user.setManagerId(null);
        user.setRoleLevel(0);
        user.setStatus(UserStatusEnum.ACTIVE.getCode());
        LocalDateTime now = LocalDateTime.now();
        user.setCreateTime(now);
        user.setUpdateTime(now);
        userMapper.insert(user);
        return user;
    }

    private LoginVO buildLoginVO(User user, String token, String tenantCode) {
        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setExpireAt(Instant.now().plus(Duration.ofHours(tokenExpireHours)).getEpochSecond());
        loginVO.setUserId(user.getId());
        loginVO.setUserName(resolveDisplayName(user));
        loginVO.setPhone(privacyProtectionUtil.displayPhone(user.getPhone(), user.getPhoneMask()));
        loginVO.setDepartmentName(resolveLoginDepartment(user.getDepartmentName(), tenantCode));
        loginVO.setPosition(resolveLoginPosition(user.getPosition(), tenantCode));
        loginVO.setTenantCode(tenantCode);
        Tenant tenant = tenantMapper.selectByTenantCode(tenantCode);
        loginVO.setTenantName(tenant == null ? defaultText(singleTenantName, tenantCode) : tenant.getTenantName());
        loginVO.setResponseKey(responseEncryptUtil.buildResponseKey(token));
        loginVO.setNeedJoinOrganization(false);
        return loginVO;
    }

    private LoginVO buildUnjoinedLoginVO(User user) {
        String token = TokenUtil.createToken(user.getId(), null);
        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setExpireAt(Instant.now().plus(Duration.ofHours(tokenExpireHours)).getEpochSecond());
        loginVO.setUserId(user.getId());
        loginVO.setUserName(resolveDisplayName(user));
        loginVO.setPhone(privacyProtectionUtil.displayPhone(user.getPhone(), user.getPhoneMask()));
        loginVO.setDepartmentName(LEGACY_STANDALONE_DEPARTMENT);
        loginVO.setPosition(LEGACY_STANDALONE_POSITION);
        loginVO.setTenantCode(null);
        loginVO.setTenantName(null);
        loginVO.setResponseKey(responseEncryptUtil.buildResponseKey(token));
        loginVO.setNeedJoinOrganization(true);
        return loginVO;
    }

    private void ensureLoginNotLocked(String failKey, Long limit, String message) {
        String failCountValue = stringRedisTemplate.opsForValue().get(failKey);
        if (failCountValue == null) {
            return;
        }
        try {
            long failCount = Long.parseLong(failCountValue);
            if (failCount >= limit) {
                throw new BusinessException(429, message);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void recordLoginFail(String failKey) {
        Long failCount = stringRedisTemplate.opsForValue().increment(failKey);
        if (failCount != null && failCount == 1L) {
            stringRedisTemplate.expire(failKey, lockMinutes, TimeUnit.MINUTES);
        }
    }

    private String normalizeClientIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "unknown";
        }
        return clientIp.replace(":", "_").replace(".", "_");
    }

    private String accountFailKeySegment(String username, String phoneHash) {
        if (phoneHash != null && !phoneHash.isBlank()) {
            return "phone:" + phoneHash;
        }
        return username;
    }

    private String normalizeTenantCode(String tenantCode) {
        if (tenantCode == null || tenantCode.trim().isEmpty()) {
            return null;
        }
        return tenantCode.trim();
    }

    private boolean isAllowedTenantUser(User user) {
        String tenantCode = user == null ? null : normalizeTenantCode(user.getTenantCode());
        return tenantCode != null && boundedTenantProperties.isTenantAllowed(tenantCode);
    }

    private String resolveLoginTenantCode(User user) {
        String tenantCode = user == null ? null : normalizeTenantCode(user.getTenantCode());
        if (tenantCode == null) {
            return defaultTenantCode();
        }
        if (!boundedTenantProperties.isTenantAllowed(tenantCode)) {
            throw new BusinessException(403, "当前组织不在系统允许范围内");
        }
        return tenantCode;
    }

    private String defaultTenantCode() {
        return boundedTenantProperties.defaultTenantCode();
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeDisplayName(String name) {
        String safeName = name == null ? "" : name.trim();
        if (safeName.isEmpty()) {
            throw new BusinessException(400, "请输入姓名");
        }
        if (safeName.length() > 30) {
            throw new BusinessException(400, "姓名不能超过30个字符");
        }
        return safeName;
    }

    private String resolveTenantCodeByOrganizationCode(String organizationCode) {
        String code = organizationCode == null ? "" : organizationCode.trim().toUpperCase();
        if (!code.matches("^[A-Z0-9]{4,32}$")) {
            throw new BusinessException(400, "组织码格式不正确");
        }

        String cachedTenantCode = stringRedisTemplate.opsForValue().get(redisKeyBuilder.cache("auth", JOIN_CODE_KEY_PART, code));
        String tenantCode = normalizeTenantCode(cachedTenantCode);
        if (tenantCode == null) {
            throw new BusinessException(404, "组织码无效或已过期");
        }
        if (!boundedTenantProperties.isTenantAllowed(tenantCode)) {
            throw new BusinessException(404, "组织码无效或已过期");
        }
        return tenantCode;
    }

    private String buildStandaloneLoginName(String phoneHash) {
        if (phoneHash == null || phoneHash.isBlank()) {
            return "wx_user_" + System.currentTimeMillis();
        }
        String suffix = phoneHash.length() <= 24 ? phoneHash : phoneHash.substring(0, 24);
        return "wx_" + suffix;
    }

    private String resolveDisplayName(User user) {
        if (user == null || user.getName() == null || user.getName().isBlank()) {
            return "微信用户";
        }
        return user.getName();
    }

    private String resolveLoginPosition(String position, String tenantCode) {
        if (tenantCode == null) {
            return null;
        }
        if (position == null
                || position.isBlank()
                || LEGACY_STANDALONE_POSITION.equals(position.trim())
                || LEGACY_PERMISSION_PLACEHOLDER.equals(position.trim())) {
            return DEFAULT_JOINED_POSITION;
        }
        return position.trim();
    }

    private String resolveLoginDepartment(String departmentName, String tenantCode) {
        if (tenantCode == null) {
            return null;
        }
        if (departmentName == null || departmentName.isBlank() || LEGACY_STANDALONE_DEPARTMENT.equals(departmentName.trim())) {
            return DEFAULT_JOINED_DEPARTMENT;
        }
        return departmentName.trim();
    }
}

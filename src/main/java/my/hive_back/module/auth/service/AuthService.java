package my.hive_back.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive.common.utils.EncryptUtil;
import my.hive.common.utils.ResponseEncryptUtil;
import my.hive.common.utils.TokenUtil;
import my.hive_back.module.auth.model.dto.LoginRequest;
import my.hive_back.module.auth.model.vo.LoginVO;
import my.hive_back.module.tenant.mapper.TenantMapper;
import my.hive_back.module.tenant.model.entity.Tenant;
import my.hive_back.module.user.mapper.UserMapper;
import my.hive_back.module.user.model.entity.User;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
/**
 * AuthService 属于小程序后端认证模块，实现核心业务编排与规则逻辑。
 */
@Service
public class AuthService {

    private static final String LOGIN_FAIL_ACCOUNT_KEY_PREFIX = "auth:login:fail:account:";
    private static final String LOGIN_FAIL_IP_KEY_PREFIX = "auth:login:fail:ip:";

    @Resource
    private UserMapper userMapper;

    @Resource
    private TenantMapper tenantMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private EncryptUtil encryptUtil;

    @Resource
    private ResponseEncryptUtil responseEncryptUtil;

    @Value("${auth.login.max-fail-count:5}")
    private Long maxFailCount;

    @Value("${auth.login.lock-minutes:15}")
    private Long lockMinutes;

    @Value("${auth.login.max-ip-fail-count:20}")
    private Long maxIpFailCount;

    @Value("${auth.token.expire-hours:24}")
    private Long tokenExpireHours;

    public LoginVO login(LoginRequest request, String clientIp) {
        String tenantCode = request.getTenantCode().trim();
        String username = request.getUsername().trim();
        String safeClientIp = normalizeClientIp(clientIp);
        String accountFailKey = LOGIN_FAIL_ACCOUNT_KEY_PREFIX + tenantCode + ":" + username;
        String ipFailKey = LOGIN_FAIL_IP_KEY_PREFIX + tenantCode + ":" + safeClientIp;

        ensureLoginNotLocked(accountFailKey, maxFailCount, "登录失败次数过多，请稍后再试");
        ensureLoginNotLocked(ipFailKey, maxIpFailCount, "当前访问过于频繁，请稍后再试");

        Tenant tenant = tenantMapper.selectByTenantCode(tenantCode);
        if (tenant == null || !Objects.equals(tenant.getStatus(), 1)) {
            recordLoginFail(accountFailKey);
            recordLoginFail(ipFailKey);
            throw new BusinessException(403, "租户不可用");
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getTenantCode, tenantCode)
                .and(wrapper -> wrapper.eq(User::getLoginName, username).or().eq(User::getPhone, username))
                .last("LIMIT 1"));

        if (user == null || !Objects.equals(user.getStatus(), 1) || !encryptUtil.matches(request.getPassword(), user.getPassword())) {
            recordLoginFail(accountFailKey);
            recordLoginFail(ipFailKey);
            throw new BusinessException(401, "账号或密码错误");
        }
        if (!encryptUtil.isBcryptHash(user.getPassword())) {
            user.setPassword(encryptUtil.encode(request.getPassword()));
            userMapper.updateById(user);
        }

        stringRedisTemplate.delete(accountFailKey);
        stringRedisTemplate.delete(ipFailKey);

        String token = TokenUtil.createToken(user.getId(), tenantCode);

        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setExpireAt(Instant.now().plus(Duration.ofHours(tokenExpireHours)).getEpochSecond());
        loginVO.setUserId(user.getId());
        loginVO.setUserName(user.getName());
        loginVO.setPhone(user.getPhone());
        loginVO.setPosition(user.getPosition());
        loginVO.setTenantCode(tenantCode);
        loginVO.setResponseKey(responseEncryptUtil.buildResponseKey(token));
        return loginVO;
    }

    public LoginVO currentUser() {
        Long userId = TenantPermissionContext.getUserId();
        String tenantCode = TenantPermissionContext.getTenantCode();
        if (userId == null || tenantCode == null) {
            throw new BusinessException(401, "请先登录");
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, userId)
                .eq(User::getTenantCode, tenantCode)
                .last("LIMIT 1"));
        if (user == null) {
            throw new BusinessException(401, "登录已失效");
        }

        LoginVO loginVO = new LoginVO();
        BeanUtils.copyProperties(user, loginVO);
        loginVO.setUserId(user.getId());
        loginVO.setUserName(user.getName());
        loginVO.setTenantCode(tenantCode);
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
}

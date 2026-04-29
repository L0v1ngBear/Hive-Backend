package my.hive_back.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive.common.privacy.PrivacyProtectionUtil;
import my.hive.common.utils.EncryptUtil;
import my.hive.common.utils.ResponseEncryptUtil;
import my.hive.common.utils.TokenUtil;
import my.hive_back.module.auth.model.dto.LoginRequest;
import my.hive_back.module.auth.model.dto.WechatLoginRequest;
import my.hive_back.module.auth.model.vo.LoginVO;
import my.hive_back.module.tenant.mapper.TenantMapper;
import my.hive_back.module.tenant.model.entity.Tenant;
import my.hive_back.module.user.mapper.UserMapper;
import my.hive_back.module.user.model.entity.User;
import my.hive_back.module.wechat.model.vo.WechatPhoneInfoVO;
import my.hive_back.module.wechat.service.WechatMiniProgramClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

    @Resource
    private PrivacyProtectionUtil privacyProtectionUtil;

    @Resource
    private WechatMiniProgramClient wechatMiniProgramClient;

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
        String phoneHash = privacyProtectionUtil.mayBePhoneKeyword(username) ? privacyProtectionUtil.hashPhone(username) : null;
        String safeClientIp = normalizeClientIp(clientIp);
        String accountFailKey = LOGIN_FAIL_ACCOUNT_KEY_PREFIX + tenantCode + ":" + accountFailKeySegment(username, phoneHash);
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
                .and(wrapper -> {
                    wrapper.eq(User::getLoginName, username);
                    if (phoneHash != null && !phoneHash.isBlank()) {
                        wrapper.or().eq(User::getPhoneHash, phoneHash);
                    }
                    // 历史数据迁移期保留明文手机号兜底，待 phone_hash 回填完成后可移除。
                    wrapper.or().eq(User::getPhone, username);
                })
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
        return buildLoginVO(user, token, tenantCode);
    }

    /**
     * 微信手机号一键登录。
     *
     * 这里没有短信验证码：小程序端经用户授权拿到 phoneCode，后端调微信官方接口换取手机号，
     * 再使用不可逆手机号哈希匹配系统中已存在的员工账号。
     */
    public LoginVO wechatLogin(WechatLoginRequest request) {
        WechatPhoneInfoVO phoneInfo = wechatMiniProgramClient.getPhoneNumber(request.getPhoneCode());
        String normalizedPhone = privacyProtectionUtil.normalizePhone(
                phoneInfo.getPurePhoneNumber() != null && !phoneInfo.getPurePhoneNumber().isBlank()
                        ? phoneInfo.getPurePhoneNumber()
                        : phoneInfo.getPhoneNumber());
        if (normalizedPhone == null) {
            throw new BusinessException("微信手机号格式异常");
        }
        String phoneHash = privacyProtectionUtil.hashPhone(normalizedPhone);
        String tenantCode = request.getTenantCode() == null ? null : request.getTenantCode().trim();
        User user = resolveWechatLoginUser(phoneHash, normalizedPhone, tenantCode);
        Tenant tenant = tenantMapper.selectByTenantCode(user.getTenantCode());
        if (tenant == null || !Objects.equals(tenant.getStatus(), 1)) {
            throw new BusinessException(403, "租户不可用");
        }
        String token = TokenUtil.createToken(user.getId(), user.getTenantCode());
        return buildLoginVO(user, token, user.getTenantCode());
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
        loginVO.setPhone(privacyProtectionUtil.displayPhone(user.getPhone(), user.getPhoneMask()));
        return loginVO;
    }

    private User resolveWechatLoginUser(String phoneHash, String normalizedPhone, String tenantCode) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getStatus, 1)
                .and(query -> query.eq(User::getPhoneHash, phoneHash)
                        // 历史数据迁移期保留明文手机号兜底，待 phone_hash 回填完成后可移除。
                        .or()
                        .eq(User::getPhone, normalizedPhone));
        if (tenantCode != null && !tenantCode.isBlank()) {
            wrapper.eq(User::getTenantCode, tenantCode);
        }
        List<User> users = userMapper.selectList(wrapper.last("LIMIT 2"));
        if (users == null || users.isEmpty()) {
            throw new BusinessException(401, "该微信手机号未绑定系统账号，请先联系管理员添加员工手机号");
        }
        if (users.size() > 1) {
            throw new BusinessException(409, "该手机号关联多个租户，请先输入租户码后再使用微信一键登录");
        }
        return users.get(0);
    }

    private LoginVO buildLoginVO(User user, String token, String tenantCode) {
        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setExpireAt(Instant.now().plus(Duration.ofHours(tokenExpireHours)).getEpochSecond());
        loginVO.setUserId(user.getId());
        loginVO.setUserName(user.getName());
        loginVO.setPhone(privacyProtectionUtil.displayPhone(user.getPhone(), user.getPhoneMask()));
        loginVO.setPosition(user.getPosition());
        loginVO.setTenantCode(tenantCode);
        loginVO.setResponseKey(responseEncryptUtil.buildResponseKey(token));
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
}

package my.hive_back.module.wechat.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive.common.external.ExternalApiGuardService;
import my.hive.common.redis.HiveRedisKeyBuilder;
import my.hive_back.module.wechat.mapper.WechatSubscribeUserMapper;
import my.hive_back.module.wechat.model.dto.WechatSubscribeRegisterRequest;
import my.hive_back.module.wechat.model.entity.WechatSubscribeUser;
import my.hive_back.module.wechat.model.vo.WechatSubscribeConfigVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 微信订阅消息服务，负责 openid 绑定、模板授权记录和服务端主动推送预留。
 */
@Slf4j
@Service
public class WechatSubscribeService {

    private static final String ACCEPT = "accept";
    private static final String REJECT = "reject";
    private static final String USED = "used";
    private static final Set<String> ALLOWED_SUBSCRIBE_STATUS = Set.of(ACCEPT, REJECT, "ban", USED);
    private static final DateTimeFormatter SUBSCRIBE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${wechat.mini-program.enabled:false}")
    private Boolean enabled;

    @Value("${wechat.mini-program.app-id:}")
    private String appId;

    @Value("${wechat.mini-program.app-secret:}")
    private String appSecret;

    @Value("${wechat.mini-program.subscribe.enabled:false}")
    private Boolean subscribeEnabled;

    @Value("${wechat.mini-program.subscribe.todo-template-id:}")
    private String todoTemplateId;

    @Value("${wechat.mini-program.subscribe.todo-operator-key:}")
    private String todoOperatorKey;

    @Value("${wechat.mini-program.subscribe.todo-operator-name:系统提醒}")
    private String todoOperatorName;

    @Value("${wechat.mini-program.subscribe.todo-title-key:thing1}")
    private String todoTitleKey;

    @Value("${wechat.mini-program.subscribe.todo-content-key:thing2}")
    private String todoContentKey;

    @Value("${wechat.mini-program.subscribe.todo-time-key:time3}")
    private String todoTimeKey;

    @Value("${external-api.guard.wechat.window-seconds:60}")
    private Integer wechatWindowSeconds;

    @Value("${external-api.guard.wechat.code2session.max-calls-per-window:120}")
    private Integer wechatCode2SessionMaxCallsPerWindow;

    @Value("${external-api.guard.wechat.access-token.max-calls-per-window:20}")
    private Integer wechatAccessTokenMaxCallsPerWindow;

    @Value("${external-api.guard.wechat.subscribe-send.max-calls-per-window:120}")
    private Integer wechatSubscribeSendMaxCallsPerWindow;

    @Resource
    private WechatSubscribeUserMapper wechatSubscribeUserMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private HiveRedisKeyBuilder redisKeyBuilder;

    @Resource
    private ExternalApiGuardService externalApiGuardService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public WechatSubscribeConfigVO getConfig() {
        boolean ready = subscribeReady();
        WechatSubscribeConfigVO vo = new WechatSubscribeConfigVO();
        vo.setEnabled(ready);
        vo.setTemplateIds(ready ? List.of(todoTemplateId.trim()) : List.of());
        vo.setTodoTemplateId(ready ? todoTemplateId.trim() : null);
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public void register(WechatSubscribeRegisterRequest request) {
        if (!subscribeReady()) {
            throw new BusinessException("微信订阅消息暂未启用");
        }
        requireWechatCredential();
        if (request == null || !hasText(request.getCode())) {
            throw new BusinessException("缺少微信登录 code");
        }
        String openid = code2Openid(request.getCode());
        Long userId = TenantPermissionContext.getUserId();
        String tenantCode = TenantPermissionContext.getTenantCode();
        if (userId == null || !hasText(tenantCode)) {
            throw new BusinessException("登录信息已失效，请重新登录后再授权订阅消息");
        }
        List<WechatSubscribeRegisterRequest.TemplateSubscribeStatus> subscriptions =
                request.getSubscriptions() == null ? List.of() : request.getSubscriptions();
        if (subscriptions.isEmpty()) {
            throw new BusinessException("订阅模板授权结果不能为空");
        }

        for (WechatSubscribeRegisterRequest.TemplateSubscribeStatus item : subscriptions) {
            if (item == null || !hasText(item.getTemplateId())) {
                continue;
            }
            String templateId = item.getTemplateId().trim();
            if (!templateId.equals(todoTemplateId.trim())) {
                throw new BusinessException("订阅模板与系统配置不一致，请刷新小程序后重试");
            }
            String status = normalizeSubscribeStatus(item.getStatus());
            WechatSubscribeUser entity = wechatSubscribeUserMapper.selectOne(new LambdaQueryWrapper<WechatSubscribeUser>()
                    .eq(WechatSubscribeUser::getTenantCode, tenantCode)
                    .eq(WechatSubscribeUser::getUserId, userId)
                    .eq(WechatSubscribeUser::getTemplateId, templateId)
                    .last("limit 1"));
            if (entity == null) {
                entity = new WechatSubscribeUser();
                entity.setTenantCode(tenantCode);
                entity.setUserId(userId);
                entity.setTemplateId(templateId);
                entity.setCreateTime(LocalDateTime.now());
            }
            entity.setOpenid(openid);
            entity.setSubscribeStatus(status);
            if (entity.getId() == null) {
                wechatSubscribeUserMapper.insert(entity);
            } else {
                wechatSubscribeUserMapper.updateById(entity);
            }
        }
    }

    /**
     * 给指定用户发送待办提醒。当前作为业务预留入口，后续订单/审批变更时可直接调用。
     */
    public boolean sendTodoReminder(Long userId, String title, String content, String pagePath) {
        return sendTodoReminder(userId, null, title, content, pagePath);
    }

    public boolean sendTodoReminder(Long userId, String operatorName, String title, String content, String pagePath) {
        if (!subscribeReady()) {
            log.info("微信订阅消息未启用，跳过待办提醒 userId={}", userId);
            return false;
        }
        String tenantCode = TenantPermissionContext.getTenantCode();
        if (!hasText(tenantCode)) {
            log.warn("微信订阅消息缺少租户上下文，跳过待办提醒 userId={} title={}", userId, title);
            return false;
        }
        requireSubscribeTemplateKeys();
        WechatSubscribeUser subscribeUser = wechatSubscribeUserMapper.selectOne(new LambdaQueryWrapper<WechatSubscribeUser>()
                .eq(WechatSubscribeUser::getTenantCode, tenantCode)
                .eq(WechatSubscribeUser::getUserId, userId)
                .eq(WechatSubscribeUser::getTemplateId, todoTemplateId.trim())
                .in(WechatSubscribeUser::getSubscribeStatus, ACCEPT, USED)
                .last("limit 1"));
        if (subscribeUser == null || !hasText(subscribeUser.getOpenid())) {
            return false;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("touser", subscribeUser.getOpenid());
        payload.put("template_id", todoTemplateId.trim());
        payload.put("page", hasText(pagePath) ? pagePath : "pages/index/index");
        payload.put("data", buildTodoTemplateData(operatorName, title, content));
        JSONObject response = sendSubscribeMessage(payload);
        Integer errCode = response.getInteger("errcode");
        if (errCode != null && errCode == 0) {
            return true;
        }
        if (errCode != null && errCode == 43101) {
            markSubscriptionStatus(subscribeUser, REJECT);
        }
        log.warn("微信订阅消息发送失败：{}", response);
        return false;
    }

    private Map<String, Object> buildTodoTemplateData(String operatorName, String title, String content) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (hasText(todoOperatorKey)) {
            data.put(todoOperatorKey.trim(), Map.of("value", limit(hasText(operatorName) ? operatorName : todoOperatorName, 10)));
        }
        data.put(todoTitleKey.trim(), Map.of("value", limit(title, 20)));
        data.put(todoContentKey.trim(), Map.of("value", limit(content, 20)));
        data.put(todoTimeKey.trim(), Map.of("value", LocalDateTime.now().format(SUBSCRIBE_TIME_FORMATTER)));
        return data;
    }

    private JSONObject sendSubscribeMessage(Map<String, Object> payload) {
        String receiver = payload == null ? "" : String.valueOf(payload.getOrDefault("touser", ""));
        protectWechatCall(
                "subscribe-send",
                externalApiGuardService.fingerprint(receiver),
                wechatSubscribeSendMaxCallsPerWindow,
                120
        );
        String accessToken = getAccessToken();
        String url = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=" + accessToken;
        return postJson(url, payload);
    }

    private void markSubscriptionStatus(WechatSubscribeUser subscribeUser, String status) {
        try {
            subscribeUser.setSubscribeStatus(status);
            wechatSubscribeUserMapper.updateById(subscribeUser);
        } catch (Exception e) {
            log.warn("更新微信订阅授权状态失败 userId={} templateId={} status={}",
                    subscribeUser.getUserId(), subscribeUser.getTemplateId(), status, e);
        }
    }

    private String getAccessToken() {
        requireWechatCredential();
        String accessTokenKey = accessTokenKey();
        String cached = stringRedisTemplate.opsForValue().get(accessTokenKey);
        if (hasText(cached)) {
            return cached;
        }
        protectWechatCall(
                "access-token",
                appSubject(),
                wechatAccessTokenMaxCallsPerWindow,
                20
        );

        String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential"
                + "&appid=" + encode(appId)
                + "&secret=" + encode(appSecret);
        JSONObject response = getJson(url);
        String accessToken = response.getString("access_token");
        if (!hasText(accessToken)) {
            throw new BusinessException("获取微信 access_token 失败：" + response.getString("errmsg"));
        }
        Integer expiresIn = response.getInteger("expires_in");
        long ttl = expiresIn == null ? 7000L : Math.max(expiresIn - 200L, 60L);
        stringRedisTemplate.opsForValue().set(accessTokenKey, accessToken, ttl, TimeUnit.SECONDS);
        return accessToken;
    }

    private String code2Openid(String code) {
        requireWechatCredential();
        protectWechatCall(
                "code2session",
                appSubject(),
                wechatCode2SessionMaxCallsPerWindow,
                120
        );
        protectWechatCall(
                "code2session-code",
                externalApiGuardService.fingerprint(code),
                3,
                3,
                Duration.ofMinutes(10)
        );

        String url = "https://api.weixin.qq.com/sns/jscode2session"
                + "?appid=" + encode(appId)
                + "&secret=" + encode(appSecret)
                + "&js_code=" + encode(code)
                + "&grant_type=authorization_code";
        JSONObject response = getJson(url);
        String openid = response.getString("openid");
        if (!hasText(openid)) {
            throw new BusinessException("获取微信 openid 失败：" + response.getString("errmsg"));
        }
        return openid;
    }

    private JSONObject getJson(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return JSON.parseObject(response.body());
        } catch (Exception e) {
            throw new BusinessException("调用微信接口失败：" + e.getMessage());
        }
    }

    private JSONObject postJson(String url, Map<String, Object> payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return JSON.parseObject(response.body());
        } catch (Exception e) {
            throw new BusinessException("发送微信订阅消息失败：" + e.getMessage());
        }
    }

    private void protectWechatCall(String action, String subject, Integer maxCalls, int defaultMaxCalls) {
        protectWechatCall(action, subject, maxCalls, defaultMaxCalls, wechatWindow());
    }

    private void protectWechatCall(String action, String subject, Integer maxCalls, int defaultMaxCalls, Duration window) {
        externalApiGuardService.checkRateLimit(
                "wechat-mini",
                action,
                subject,
                maxCalls == null ? defaultMaxCalls : Math.max(1, maxCalls),
                window
        );
    }

    private Duration wechatWindow() {
        int seconds = wechatWindowSeconds == null ? 60 : Math.max(1, wechatWindowSeconds);
        return Duration.ofSeconds(seconds);
    }

    private void requireWechatCredential() {
        if (!hasText(appId) || !hasText(appSecret)) {
            throw new BusinessException("微信小程序 appId/appSecret 未配置");
        }
    }

    private boolean subscribeReady() {
        return Boolean.TRUE.equals(enabled) && Boolean.TRUE.equals(subscribeEnabled) && hasText(todoTemplateId);
    }

    private void requireSubscribeTemplateKeys() {
        if (hasText(todoOperatorKey)) {
            requireTemplateKey(todoOperatorKey, "催办人字段");
        }
        requireTemplateKey(todoTitleKey, "待办标题字段");
        requireTemplateKey(todoContentKey, "待办内容字段");
        requireTemplateKey(todoTimeKey, "待办时间字段");
        if (templateKeysDuplicated(todoOperatorKey, todoTitleKey, todoContentKey, todoTimeKey)) {
            throw new BusinessException("微信订阅消息模板字段不能重复");
        }
    }

    private boolean templateKeysDuplicated(String... keys) {
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (String key : keys) {
            if (!hasText(key)) {
                continue;
            }
            if (!seen.add(key.trim())) {
                return true;
            }
        }
        return false;
    }

    private void requireTemplateKey(String key, String label) {
        if (!hasText(key) || !key.trim().matches("^[A-Za-z_]+\\d+$")) {
            throw new BusinessException("微信订阅消息" + label + "配置不正确");
        }
    }

    private String normalizeSubscribeStatus(String status) {
        String normalized = hasText(status) ? status.trim() : REJECT;
        if (!ALLOWED_SUBSCRIBE_STATUS.contains(normalized)) {
            throw new BusinessException("订阅授权状态不正确");
        }
        return normalized;
    }

    private String accessTokenKey() {
        return redisKeyBuilder.cache("wechat", "mini", "access-token", appId);
    }

    private String appSubject() {
        return "app-" + appId;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String limit(String value, int maxLength) {
        String text = hasText(value) ? value.trim() : "-";
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}

package my.hive_back.module.wechat.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 微信订阅消息服务，负责 openid 绑定、模板授权记录和服务端主动推送预留。
 */
@Slf4j
@Service
public class WechatSubscribeService {

    private static final String ACCESS_TOKEN_KEY = "wechat:mini:access_token";
    private static final String ACCEPT = "accept";

    @Value("${wechat.mini-program.enabled:false}")
    private Boolean enabled;

    @Value("${wechat.mini-program.app-id:}")
    private String appId;

    @Value("${wechat.mini-program.app-secret:}")
    private String appSecret;

    @Value("${wechat.mini-program.subscribe.todo-template-id:}")
    private String todoTemplateId;

    @Resource
    private WechatSubscribeUserMapper wechatSubscribeUserMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public WechatSubscribeConfigVO getConfig() {
        WechatSubscribeConfigVO vo = new WechatSubscribeConfigVO();
        vo.setEnabled(Boolean.TRUE.equals(enabled) && hasText(todoTemplateId));
        vo.setTemplateIds(hasText(todoTemplateId) ? List.of(todoTemplateId) : List.of());
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public void register(WechatSubscribeRegisterRequest request) {
        if (!Boolean.TRUE.equals(enabled)) {
            throw new BusinessException("微信订阅消息暂未启用");
        }
        if (!hasText(appId) || !hasText(appSecret)) {
            throw new BusinessException("微信小程序 appId/appSecret 未配置");
        }
        String openid = code2Openid(request.getCode());
        Long userId = TenantPermissionContext.getUserId();
        String tenantCode = TenantPermissionContext.getTenantCode();
        List<WechatSubscribeRegisterRequest.TemplateSubscribeStatus> subscriptions =
                request.getSubscriptions() == null ? List.of() : request.getSubscriptions();

        for (WechatSubscribeRegisterRequest.TemplateSubscribeStatus item : subscriptions) {
            if (item == null || !hasText(item.getTemplateId())) {
                continue;
            }
            WechatSubscribeUser entity = wechatSubscribeUserMapper.selectOne(new LambdaQueryWrapper<WechatSubscribeUser>()
                    .eq(WechatSubscribeUser::getTenantCode, tenantCode)
                    .eq(WechatSubscribeUser::getUserId, userId)
                    .eq(WechatSubscribeUser::getTemplateId, item.getTemplateId())
                    .last("limit 1"));
            if (entity == null) {
                entity = new WechatSubscribeUser();
                entity.setTenantCode(tenantCode);
                entity.setUserId(userId);
                entity.setTemplateId(item.getTemplateId());
                entity.setCreateTime(LocalDateTime.now());
            }
            entity.setOpenid(openid);
            entity.setSubscribeStatus(hasText(item.getStatus()) ? item.getStatus() : ACCEPT);
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
        if (!Boolean.TRUE.equals(enabled) || !hasText(todoTemplateId)) {
            log.info("微信订阅消息未启用，跳过待办提醒 userId={}", userId);
            return false;
        }
        WechatSubscribeUser subscribeUser = wechatSubscribeUserMapper.selectOne(new LambdaQueryWrapper<WechatSubscribeUser>()
                .eq(WechatSubscribeUser::getTenantCode, TenantPermissionContext.getTenantCode())
                .eq(WechatSubscribeUser::getUserId, userId)
                .eq(WechatSubscribeUser::getTemplateId, todoTemplateId)
                .eq(WechatSubscribeUser::getSubscribeStatus, ACCEPT)
                .last("limit 1"));
        if (subscribeUser == null || !hasText(subscribeUser.getOpenid())) {
            return false;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("touser", subscribeUser.getOpenid());
        payload.put("template_id", todoTemplateId);
        payload.put("page", hasText(pagePath) ? pagePath : "pages/todo/todo");
        payload.put("data", buildTodoTemplateData(title, content));
        return sendSubscribeMessage(payload);
    }

    private Map<String, Object> buildTodoTemplateData(String title, String content) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("thing1", Map.of("value", limit(title, 20)));
        data.put("thing2", Map.of("value", limit(content, 20)));
        data.put("time3", Map.of("value", LocalDateTime.now().toString().replace('T', ' ')));
        return data;
    }

    private boolean sendSubscribeMessage(Map<String, Object> payload) {
        String accessToken = getAccessToken();
        String url = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=" + accessToken;
        JSONObject response = postJson(url, payload);
        Integer errCode = response.getInteger("errcode");
        if (errCode != null && errCode == 0) {
            return true;
        }
        log.warn("微信订阅消息发送失败：{}", response);
        return false;
    }

    private String getAccessToken() {
        String cached = stringRedisTemplate.opsForValue().get(ACCESS_TOKEN_KEY);
        if (hasText(cached)) {
            return cached;
        }
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
        stringRedisTemplate.opsForValue().set(ACCESS_TOKEN_KEY, accessToken, ttl, TimeUnit.SECONDS);
        return accessToken;
    }

    private String code2Openid(String code) {
        if (!hasText(code)) {
            throw new BusinessException("缺少微信登录 code");
        }
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

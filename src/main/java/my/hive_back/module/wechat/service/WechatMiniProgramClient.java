package my.hive_back.module.wechat.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import my.hive.common.exception.BusinessException;
import my.hive.common.external.ExternalApiGuardService;
import my.hive.common.redis.HiveRedisKeyBuilder;
import my.hive_back.module.wechat.model.vo.WechatPhoneInfoVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 微信小程序服务端客户端。
 *
 * <p>统一封装 access_token、code2session 和手机号授权接口，所有外部调用都经过
 * Redis 限流保护；access_token 继续使用 Redis 缓存，避免频繁打到微信接口。</p>
 */
@Service
public class WechatMiniProgramClient {

    @Value("${wechat.mini-program.enabled:false}")
    private Boolean enabled;

    @Value("${wechat.mini-program.app-id:}")
    private String appId;

    @Value("${wechat.mini-program.app-secret:}")
    private String appSecret;

    @Value("${external-api.guard.wechat.window-seconds:60}")
    private Integer wechatWindowSeconds;

    @Value("${external-api.guard.wechat.code2session.max-calls-per-window:120}")
    private Integer wechatCode2SessionMaxCallsPerWindow;

    @Value("${external-api.guard.wechat.phone-number.max-calls-per-window:60}")
    private Integer wechatPhoneNumberMaxCallsPerWindow;

    @Value("${external-api.guard.wechat.access-token.max-calls-per-window:20}")
    private Integer wechatAccessTokenMaxCallsPerWindow;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private HiveRedisKeyBuilder redisKeyBuilder;

    @Resource
    private ExternalApiGuardService externalApiGuardService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled) && hasText(appId) && hasText(appSecret);
    }

    /**
     * 通过 wx.login 的 code 换取 openid。
     */
    public String code2Openid(String code) {
        requireEnabled();
        if (!hasText(code)) {
            throw new BusinessException("缺少微信登录 code");
        }
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
        Integer errCode = response.getInteger("errcode");
        if (errCode != null && errCode != 0) {
            throw new BusinessException("获取微信 openid 失败：" + response.getString("errmsg"));
        }
        String openid = response.getString("openid");
        if (!hasText(openid)) {
            throw new BusinessException("获取微信 openid 失败：" + response.getString("errmsg"));
        }
        return openid;
    }

    /**
     * 通过 getPhoneNumber 返回的一次性 code 换取手机号，用于微信一键登录。
     */
    public WechatPhoneInfoVO getPhoneNumber(String phoneCode) {
        requireEnabled();
        if (!hasText(phoneCode)) {
            throw new BusinessException("缺少微信手机号授权码");
        }
        protectWechatCall(
                "phone-number",
                appSubject(),
                wechatPhoneNumberMaxCallsPerWindow,
                60
        );
        protectWechatCall(
                "phone-number-code",
                externalApiGuardService.fingerprint(phoneCode),
                3,
                3,
                Duration.ofMinutes(10)
        );

        JSONObject response = requestPhoneNumberByCode(phoneCode, getAccessToken());
        Integer errCode = response.getInteger("errcode");
        if (Integer.valueOf(40001).equals(errCode) || Integer.valueOf(42001).equals(errCode)) {
            clearAccessToken();
            response = requestPhoneNumberByCode(phoneCode, getAccessToken());
            errCode = response.getInteger("errcode");
        }
        if (errCode == null || errCode != 0) {
            throw new BusinessException("获取微信手机号失败：" + response.getString("errmsg"));
        }
        JSONObject phoneInfo = response.getJSONObject("phone_info");
        if (phoneInfo == null || (!hasText(phoneInfo.getString("phoneNumber")) && !hasText(phoneInfo.getString("purePhoneNumber")))) {
            throw new BusinessException("微信未返回手机号");
        }
        return toPhoneInfo(phoneInfo);
    }

    public String getAccessToken() {
        requireEnabled();
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

    public JSONObject postJson(String url, Map<String, Object> payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return JSON.parseObject(response.body());
        } catch (Exception e) {
            throw new BusinessException("调用微信接口失败：" + e.getMessage());
        }
    }

    private JSONObject requestPhoneNumberByCode(String phoneCode, String accessToken) {
        String url = "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=" + accessToken;
        return postJson(url, Map.of("code", phoneCode));
    }

    private WechatPhoneInfoVO toPhoneInfo(JSONObject phoneInfo) {
        WechatPhoneInfoVO vo = new WechatPhoneInfoVO();
        vo.setPhoneNumber(phoneInfo.getString("phoneNumber"));
        vo.setPurePhoneNumber(phoneInfo.getString("purePhoneNumber"));
        vo.setCountryCode(phoneInfo.getString("countryCode"));
        return vo;
    }

    private void clearAccessToken() {
        stringRedisTemplate.delete(accessTokenKey());
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

    private void requireEnabled() {
        if (!Boolean.TRUE.equals(enabled)) {
            throw new BusinessException("微信小程序能力暂未启用");
        }
        if (!hasText(appId) || !hasText(appSecret)) {
            throw new BusinessException("微信小程序 appId/appSecret 未配置");
        }
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
}

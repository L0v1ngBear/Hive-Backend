package my.hive_back.common.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import jakarta.annotation.PostConstruct;
import my.hive_back.common.auth.AuthUserInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component
public class TokenUtil {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    @Value("${auth.token.secret:hive-back-dev-token-secret}")
    private String tokenSecret;

    @Value("${auth.token.expire-hours:24}")
    private Long tokenExpireHours;

    private static String secret;
    private static Long expireHours;
    private static Mac macTemplate;

    @PostConstruct
    public void init() {
        secret = tokenSecret;
        expireHours = tokenExpireHours;
        try {
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            macTemplate = Mac.getInstance(HMAC_ALGORITHM);
            macTemplate.init(keySpec);
        } catch (Exception ex) {
            throw new IllegalStateException("token util init failed", ex);
        }
    }

    public static String createToken(Long userId, String tenantCode) {
        long now = Instant.now().getEpochSecond();
        long expireAt = Instant.now().plus(Duration.ofHours(expireHours)).getEpochSecond();

        Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", "JWT"
        );
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("tenantCode", tenantCode);
        payload.put("iat", now);
        payload.put("exp", expireAt);

        String headerPart = encode(JSON.toJSONString(header));
        String payloadPart = encode(JSON.toJSONString(payload));
        String content = headerPart + "." + payloadPart;
        String signature = sign(content);
        return content + "." + signature;
    }

    public static AuthUserInfo parseToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }

        String content = parts[0] + "." + parts[1];
        if (!Objects.equals(sign(content), parts[2])) {
            return null;
        }

        Map<String, Object> payload = JSON.parseObject(
                new String(URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8),
                new TypeReference<Map<String, Object>>() {}
        );
        if (payload == null) {
            return null;
        }

        Long expireAtValue = toLong(payload.get("exp"));
        if (expireAtValue == null || Instant.now().getEpochSecond() > expireAtValue) {
            return null;
        }

        AuthUserInfo authUserInfo = new AuthUserInfo();
        authUserInfo.setUserId(toLong(payload.get("userId")));
        authUserInfo.setTenantCode((String) payload.get("tenantCode"));
        authUserInfo.setExpireAt(expireAtValue);
        return authUserInfo;
    }

    private static String encode(String raw) {
        return URL_ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String sign(String content) {
        try {
            Mac mac = (Mac) macTemplate.clone();
            byte[] signature = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return URL_ENCODER.encodeToString(signature);
        } catch (CloneNotSupportedException ex) {
            try {
                SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
                Mac mac = Mac.getInstance(HMAC_ALGORITHM);
                mac.init(keySpec);
                return URL_ENCODER.encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
            } catch (Exception innerEx) {
                throw new IllegalStateException("token sign failed", innerEx);
            }
        }
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

package my.hive_back.common.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import my.hive_back.common.model.EncryptPayload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
/**
 * ResponseEncryptUtil 属于小程序后端通用能力层，提供可复用的工具方法。
 */
@Component
public class ResponseEncryptUtil {

    public static final String RESPONSE_ALGORITHM = "AES-256-CBC+HMAC-SHA256";
    private static final int IV_LENGTH = 16;

    @Resource
    private ObjectMapper objectMapper;

    @Value("${security.response.key}")
    private String responseKey;

    private final SecureRandom secureRandom = new SecureRandom();
    private Base64.Encoder base64Encoder;
    private byte[] keyMaterial;

    @PostConstruct
    public void init() {
        base64Encoder = Base64.getEncoder();
        keyMaterial = Base64.getDecoder().decode(responseKey);
    }

    public String buildResponseKey(String token) {
        return responseKey;
    }

    public EncryptPayload encrypt(String token, Object data) {
        try {
            byte[] cipherKey = Arrays.copyOfRange(keyMaterial, 0, 32);
            byte[] macKey = Arrays.copyOfRange(keyMaterial, 32, 64);
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cipherKey, "AES"), new IvParameterSpec(iv));
            byte[] plainBytes = objectMapper.writeValueAsBytes(data);
            byte[] cipherBytes = cipher.doFinal(plainBytes);
            String ivBase64 = base64Encoder.encodeToString(iv);
            String cipherBase64 = base64Encoder.encodeToString(cipherBytes);

            EncryptPayload payload = new EncryptPayload();
            payload.setIv(ivBase64);
            payload.setCiphertext(cipherBase64);
            payload.setMac(sign(macKey, ivBase64 + "." + cipherBase64));
            return payload;
        } catch (Exception ex) {
            throw new IllegalStateException("response encrypt failed", ex);
        }
    }

    private String sign(byte[] macKey, String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(macKey, "HmacSHA256"));
            return base64Encoder.encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("response sign failed", ex);
        }
    }
}

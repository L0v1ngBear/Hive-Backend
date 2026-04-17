package my.hive_back.common.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;
/**
 * EncryptUtil 属于小程序后端通用能力层，提供可复用的工具方法。
 */
@Component
public class EncryptUtil {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]?\\$.{56}$");

    public String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("password cannot be blank");
        }
        return ENCODER.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String hashedPassword) {
        if (rawPassword == null || rawPassword.isBlank() || hashedPassword == null || hashedPassword.isBlank()) {
            return false;
        }
        if (isBcryptHash(hashedPassword)) {
            return ENCODER.matches(rawPassword, hashedPassword);
        }
        String md5Password = DigestUtils.md5DigestAsHex(rawPassword.getBytes(StandardCharsets.UTF_8));
        return Objects.equals(rawPassword, hashedPassword) || Objects.equals(md5Password, hashedPassword);
    }

    public boolean isBcryptHash(String password) {
        return password != null && BCRYPT_PATTERN.matcher(password).matches();
    }
}

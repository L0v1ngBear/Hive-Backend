package my.hive_back.common.utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
/**
 * BarCodeUtil 属于小程序后端通用能力层，提供可复用的工具方法。
 */
@Component
public class BarCodeUtil {

    @Value("${redis.key-prefix.barCode.prefix}")
    private static String BARCODE_DAILY_NUMBER_KEY_PREFIX;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");
    private static final String BARCODE_PREFIX = "CL";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisUtil redisUtil;

    /**
     * 生成条码（核心方法：线程安全且保证 Redis 计数原子性）
     */
    public String createBarCode(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new IllegalArgumentException("租户Code不能为空");
        }

        String tenantPart = normalizeTenantCode(tenantCode.trim());
        String datePart = LocalDate.now().format(DATE_FORMATTER);
        String redisKey = BARCODE_DAILY_NUMBER_KEY_PREFIX + tenantPart + ":" + datePart;

        // 使用 Lua 脚本保证自增和设置过期时间的原子性，防止 Key 永久存在
        Long currentSeq = incrementAndExpire(redisKey);

        String baseCode = BARCODE_PREFIX + tenantPart + datePart + String.format("%04d", currentSeq);
        return baseCode + generateCheckCode(baseCode);
    }

    /**
     * 生成条码图片
     */
    public BufferedImage createBarCodeImage(String text, int width, int height) {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1); // 设置边距
        try {
            BitMatrix bitMatrix = new Code128Writer().encode(text, BarcodeFormat.CODE_128, width, height, hints);
            return MatrixToImageWriter.toBufferedImage(bitMatrix);
        } catch (Exception e) {
            throw new RuntimeException("生成条码图片失败", e);
        }
    }

    /**
     * Redis 原子自增并设置过期时间
     */
    private Long incrementAndExpire(String key) {
        String script = "local c = redis.call('incr', KEYS[1]); " +
                "if c == 1 then redis.call('expire', KEYS[1], ARGV[1]) end; " +
                "return c;";
        return stringRedisTemplate.execute(
                new DefaultRedisScript<>(script, Long.class),
                Collections.singletonList(key),
                String.valueOf(redisUtil.getSecondsToNextDay())
        );
    }

    private String normalizeTenantCode(String tenantCode) {
        // 简单处理：转大写并截取/填充至6位
        String clean = tenantCode.toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (clean.length() >= 6) {
            return clean.substring(clean.length() - 6);
        }
        return String.format("%6s", clean).replace(' ', '0');
    }

    private String generateCheckCode(String baseCode) {
        int sum = 0;
        for (int i = 0; i < baseCode.length(); i++) {
            sum += baseCode.charAt(i) * ((i % 2 == 0) ? 1 : 3);
        }
        return String.format("%02d", sum % 100);
    }
}

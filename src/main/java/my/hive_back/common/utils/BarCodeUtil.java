package my.hive_back.common.utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Component
public class BarCodeUtil {

    private static final String BARCODE_DAILY_NUMBER_KEY_PREFIX = "barcode:number:";

    // 日期格式化器（年2位+月2位+日2位）
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");
    // 随机数生成器（线程安全）
    private static final Random RANDOM = new Random();
    // 条码前缀（固定CL，便于识别是布卷条码）
    private static final String BARCODE_PREFIX = "CL";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public BufferedImage createBarCodeImage(String text, int width, int height) {
        Map<EncodeHintType, Object> hints = new HashMap<EncodeHintType, Object>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        try {
            BitMatrix bitMatrix = new Code128Writer().encode(text, BarcodeFormat.CODE_128, width, height, hints);
            return MatrixToImageWriter.toBufferedImage(bitMatrix);
        } catch (Exception e) {
            throw new RuntimeException("生成条码图片失败");
        }
    }

    public String createBarCode(String tenantCode) {
        // 1. 租户Code非空校验（提示语适配Code）
        if (tenantCode == null || tenantCode.trim().isEmpty()) {
            throw new IllegalArgumentException("租户Code不能为空");
        }

        // 2. 租户Code处理（核心优化：适配非纯数字）
        String tenantPart = normalizeTenantCode(tenantCode.trim());

        // 3. 日期部分（yyMMdd，固定6位）
        String datePart = LocalDate.now().format(DATE_FORMATTER);

        // 4. 生成RedisKey（核心：按租户+日期隔离）
        String redisKey = BARCODE_DAILY_NUMBER_KEY_PREFIX + tenantPart + "_" + datePart;

        // 从redis中获取number
        Long currentSeq = stringRedisTemplate.opsForValue().increment(redisKey, 1);
        // 处理首次初始化（防止null）
        if (currentSeq == null) {
            stringRedisTemplate.opsForValue().set(redisKey, "1");
            currentSeq = 1L;
            // 设置过期时间：次日0点失效（保证按天重置）
            long expireSeconds = getSecondsToNextDay();
            stringRedisTemplate.expire(redisKey, expireSeconds, TimeUnit.SECONDS);
        } else if (currentSeq == 1) {
            // 首次递增时设置过期时间（避免重复设置）
            long expireSeconds = getSecondsToNextDay();
            stringRedisTemplate.expire(redisKey, expireSeconds, TimeUnit.SECONDS);
        }

        // 序列增加1
        stringRedisTemplate.opsForValue().increment(redisKey, 1);

        // 4. 拼接基础条码
        String baseCode = BARCODE_PREFIX + tenantPart + datePart + currentSeq;

        // 6. 生成2位校验位（防扫码/输入错误）
        String checkCode = generateCheckCode(baseCode);

        // 7. 最终条码
        return baseCode + checkCode;

    }

    /**
     * 租户Code归一化处理（核心：适配非纯数字）
     * 规则：
     * 1. 过滤特殊字符，仅保留字母/数字（符合Code128扫码规范）；
     * 2. 统一转为6位（超长截取后6位，不足补0）；
     * 3. 字母转大写，保证一致性。
     */
    private static String normalizeTenantCode(String tenantCode) {
        // 步骤1：过滤特殊字符，仅保留字母和数字
        String cleanCode = tenantCode.replaceAll("[^a-zA-Z0-9]", "");
        if (cleanCode.isEmpty()) {
            throw new IllegalArgumentException("租户Code过滤特殊字符后为空，请检查格式");
        }

        // 步骤2：转大写（避免大小写不一致导致隔离失效）
        cleanCode = cleanCode.toUpperCase();

        // 步骤3：归一化到6位（超长截取后6位，不足补0）
        if (cleanCode.length() >= 6) {
            return cleanCode.substring(cleanCode.length() - 6);
        } else {
            // 不足6位补0（补在末尾，不影响可读性）
            return String.format("%-6s", cleanCode).replace(' ', '0');
        }
    }

    /**
     * 生成2位校验位（基于ASCII码求和取模，防错误）
     */
    private static String generateCheckCode(String baseCode) {
        int sum = 0;
        for (int i = 0; i < baseCode.length(); i++) {
            // 奇数位×1，偶数位×3，提升校验准确性
            char c = baseCode.charAt(i);
            int coeff = (i + 1) % 2 == 0 ? 3 : 1;
            sum += (int) c * coeff;
        }
        // 取模100，补0到2位
        int checkNum = sum % 100;
        return String.format("%02d", checkNum);
    }

    // 计算距离下一天0点的秒数（用于设置过期时间）
    private long getSecondsToNextDay() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        // 次日0点的时间戳（毫秒）
        long tomorrowZero = tomorrow.atStartOfDay().toEpochSecond(java.time.ZoneOffset.ofHours(8)) * 1000;
        long now = System.currentTimeMillis();
        return (tomorrowZero - now) / 1000;
    }
}

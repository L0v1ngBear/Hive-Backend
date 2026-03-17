package my.hive_back.common.utils;

import jakarta.annotation.Resource;
import my.hive_back.module.tenant.model.entity.TenantAttendanceRule;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class RedisUtil {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 计算距离下一天0点的秒数（用于设置过期时间）
      */
    public long getSecondsToNextDay() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        // 次日0点的时间戳（毫秒）
        long tomorrowZero = tomorrow.atStartOfDay().toEpochSecond(java.time.ZoneOffset.ofHours(8)) * 1000;
        long now = System.currentTimeMillis();
        return (tomorrowZero - now) / 1000;
    }

    public <T> T getHashValueAndConvert(String hashKey, String tenantCode, Class<T> targetType, T defaultValue) {
        // 1. 获取 Redis Hash 中的值，避免 NPE
        String value = (String) stringRedisTemplate.opsForHash().get(hashKey, tenantCode);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        // 2. 根据目标类型进行安全转换
        try {
            if (targetType == Double.class) {
                return targetType.cast(Double.parseDouble(value));
            } else if (targetType == Integer.class) {
                return targetType.cast(Integer.parseInt(value));
            } else {
                // 不支持的类型返回默认值
                return defaultValue;
            }
        } catch (NumberFormatException e) {
            // 转换失败打印日志（建议用日志框架，如 logback/log4j2）
            System.err.printf("转换Redis值失败，hashKey=%s, tenantCode=%s, value=%s%n", hashKey, tenantCode, value);
            return defaultValue;
        }
    }

    public void pushHashValue(String companyAttendanceRuleKey, String tenantCode, TenantAttendanceRule rule) {
        stringRedisTemplate.opsForHash().put(companyAttendanceRuleKey, tenantCode, rule);
    }
}

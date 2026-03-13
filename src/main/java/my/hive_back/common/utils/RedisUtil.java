package my.hive_back.common.utils;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class RedisUtil {

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
}

package my.hive_back.common.utils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

public class TimeUtil {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    // 获取当前东八区时间（推荐）
    public static LocalDateTime now() {
        return LocalDateTime.now(DEFAULT_ZONE);
    }

    public static LocalTime nowTime() {
        return LocalTime.now(DEFAULT_ZONE);
    }
}

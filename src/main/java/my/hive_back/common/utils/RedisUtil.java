package my.hive_back.common.utils;

import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

@Slf4j
@Component
public class RedisUtil {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 计算距离下一天0点的秒数（用于设置过期时间）
     */
    public long getSecondsToNextDay() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        // 次日0点的时间戳（毫秒）- 修复时区和计算逻辑
        long tomorrowZero = tomorrow.atStartOfDay().toEpochSecond(ZoneOffset.ofHours(8)) * 1000;
        long now = System.currentTimeMillis();
        return (tomorrowZero - now) / 1000;
    }


    /**
     * 向Redis Hash中存入值（支持任意对象，基于FastJSON序列化）
     * @param key Redis Hash的主Key
     * @param hashKey Hash的子Key
     * @param value 要存储的值（对象/字符串）
     */
    public void pushHashValue(String key, String hashKey, Object value) {
        try {
            // 核心：使用FastJSON将对象序列化为JSON字符串
            String valueStr;
            if (value instanceof String) {
                valueStr = (String) value;
            } else {
                valueStr = JSON.toJSONString(value);
            }
            // 使用StringRedisTemplate操作，避免类型错误
            HashOperations<String, String, String> hashOps = stringRedisTemplate.opsForHash();
            hashOps.put(key, hashKey, valueStr);
        } catch (Exception e) {
            throw new RuntimeException("Redis Hash值FastJSON序列化失败：" + e.getMessage(), e);
        }
    }

    /**
     * 从Redis Hash中读取值并反序列化为指定对象（基于FastJSON）
     * @param key Redis Hash的主Key
     * @param hashKey Hash的子Key
     * @param clazz 目标对象类型
     * @return 反序列化后的对象（null表示无值）
     */
    public <T> T getHashValue(String key, String hashKey, Class<T> clazz) {
        HashOperations<String, String, String> hashOps = stringRedisTemplate.opsForHash();
        String valueStr = hashOps.get(key, hashKey);
        if (valueStr == null || valueStr.isEmpty()) {
            return null;
        }
        try {
            // FastJSON反序列化：JSON字符串 → 指定类型对象
            return JSON.parseObject(valueStr, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Redis Hash值FastJSON反序列化失败：" + e.getMessage(), e);
        }
    }

    public void deleteHashKey(String redisHashKey) {
        stringRedisTemplate.delete(redisHashKey);
    }
}
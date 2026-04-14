package my.hive_back.common.utils;

import jakarta.annotation.Resource;
import my.hive_back.common.context.TenantPermissionContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Component
public class CodeGeneratorUtil {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 日期格式化器（精确到天，意味着流水号每天重置）
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 核心方法：生成多租户隔离的高并发业务编码
     * * @param prefix 业务前缀 (如 "LQ", "SO", "PO")
     * @param length 流水号长度 (如 4位 或 6位)
     * @return 格式：前缀 + 年月日 + 流水号 (例如 LQ202604010001)
     */
    public String generateCode(String prefix, int length) {
        // 1. 获取当前租户ID (确保多租户流水号互相隔离，互不干扰)
        String tenantCode = TenantPermissionContext.getTenantCode();

        if (tenantCode == null) {
            // 防御性编程：如果没有租户上下文（例如系统定时任务跑批），给个默认值 0
            tenantCode = "0";
        }

        // 2. 获取当前日期 (如 20260401)
        String dateStr = LocalDateTime.now().format(DATE_FORMATTER);

        // 3. 构建 Redis Key (按 租户 + 业务 + 日期 隔离)
        // 例如：sys:seq:108:LQ:20260401
        String redisKey = String.format("sys:seq:%s:%s:%s", tenantCode, prefix, dateStr);

        // 4. 利用 Redis 的原子递增特性生成流水号
        Long increment = stringRedisTemplate.opsForValue().increment(redisKey);

        // 5. 如果是今天的第一单，设置过期时间
        // 设置 25 小时过期，确保跨天后昨天的 Key 能被自动清理，防止 Redis 内存泄漏
        if (increment != null && increment == 1) {
            stringRedisTemplate.expire(redisKey, 25, TimeUnit.HOURS);
        }

        // 6. 拼接最终编码，流水号不足位数前面补0
        String seqStr = String.format("%0" + length + "d", increment);

        // 返回最终字符串
        return prefix + dateStr + seqStr;
    }

    /**
     * 生成请假编码
     * 格式：LQ202604010001 (前缀LQ + 8位日期 + 4位流水)
     */
    public String generateLeaveCode() {
        return generateCode("LQ", 4);
    }

    /**
     * 生成销售订单号
     * 格式：SO20260401000001 (前缀SO + 8位日期 + 6位流水)
     */
    public String generateSalesOrderCode() {
        return generateCode("SO", 6);
    }

    /**
     * 生成生产订单号
     * 格式：PO20260401000001 (前缀PO + 8位日期 + 6位流水)
     */
    public String generateProductionOrderCode() {
        return generateCode("PO", 6);
    }

    public String generateOutboundOrderNo() {
        return generateCode("CK", 6);
    }

    public String generateFinanceApprovalCode() {
        return generateCode("FA", 6);
    }
}

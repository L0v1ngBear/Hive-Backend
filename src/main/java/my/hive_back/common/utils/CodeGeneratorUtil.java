package my.hive_back.common.utils;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;

@Component
public class CodeGeneratorUtil {

    // 固定前缀
    private static final String LEAVE_PREFIX = "LQ";
    // 日期格式化器（线程安全）
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    // 随机数生成器
    private static final Random RANDOM = new Random();
    // 随机数位数（可根据业务调整，4位=0000-9999，足够单日使用）
    private static final int RANDOM_DIGITS = 4;

    /**
     * 生成请假编码（核心方法）
     * @return 格式：LQ + 年月日 + 4位随机数
     */
    public String generateLeaveCode() {
        // 1. 获取当前日期（格式：yyyyMMdd）
        String dateStr = LocalDate.now().format(DATE_FORMATTER);

        // 2. 生成4位随机数（不足补0）
        int randomNum = RANDOM.nextInt((int) Math.pow(10, RANDOM_DIGITS));
        String randomStr = String.format("%0" + RANDOM_DIGITS + "d", randomNum);

        // 3. 拼接最终编码
        return LEAVE_PREFIX + dateStr + randomStr;
    }

}

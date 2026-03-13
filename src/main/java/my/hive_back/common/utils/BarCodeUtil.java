package my.hive_back.common.utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class BarCodeUtil {

    // 打印延迟时间（秒），可根据需求调整
    private static final int PRINT_DELAY_SECONDS = 3;
    // 本地缓存条码的容器（线程安全）
    private final BlockingQueue<String> barcodeCache = new LinkedBlockingQueue<>();
    // 标记是否已提交延迟打印任务（避免重复提交）
    private final AtomicBoolean isPrintTaskSubmitted = new AtomicBoolean(false);
    // 单线程池：处理延迟打印任务（保证顺序）
    private final ScheduledExecutorService printExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "barcode-print-thread");
        t.setDaemon(true); // 守护线程，应用关闭时自动退出
        return t;
    });

    // ========== 原有常量 ==========
    private static final String BARCODE_DAILY_NUMBER_KEY_PREFIX = "barcode:number:";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");
    private static final Random RANDOM = new Random();
    private static final String BARCODE_PREFIX = "CL";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisUtil redisUtil;

    // ========== 原有方法（不变） ==========
    public BufferedImage createBarCodeImage(String text, int width, int height) {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        try {
            BitMatrix bitMatrix = new Code128Writer().encode(text, BarcodeFormat.CODE_128, width, height, hints);
            return MatrixToImageWriter.toBufferedImage(bitMatrix);
        } catch (Exception e) {
            throw new RuntimeException("生成条码图片失败", e);
        }
    }


    public String createBarCode(String tenantCode) {
        // 1. 原有条码生成逻辑
        if (tenantCode == null || tenantCode.trim().isEmpty()) {
            throw new IllegalArgumentException("租户Code不能为空");
        }
        String tenantPart = normalizeTenantCode(tenantCode.trim());
        String datePart = LocalDate.now().format(DATE_FORMATTER);
        String redisKey = BARCODE_DAILY_NUMBER_KEY_PREFIX + tenantPart + "_" + datePart;

        Long currentSeq = stringRedisTemplate.opsForValue().increment(redisKey, 1);
        if (currentSeq == null) {
            stringRedisTemplate.opsForValue().set(redisKey, "1");
            currentSeq = 1L;
            long expireSeconds = redisUtil.getSecondsToNextDay();
            stringRedisTemplate.expire(redisKey, expireSeconds, TimeUnit.SECONDS);
        } else if (currentSeq == 1) {
            long expireSeconds = redisUtil.getSecondsToNextDay();
            stringRedisTemplate.expire(redisKey, expireSeconds, TimeUnit.SECONDS);
        }

        String baseCode = BARCODE_PREFIX + tenantPart + datePart + currentSeq;
        String checkCode = generateCheckCode(baseCode);
        String finalBarcode = baseCode + checkCode;

        // 2. 新增：将生成的条码加入缓存
        barcodeCache.offer(finalBarcode);

        // 3. 新增：提交延迟打印任务（仅第一次触发时提交）
        if (isPrintTaskSubmitted.compareAndSet(false, true)) {
            printExecutor.schedule(this::batchPrintBarcodes, PRINT_DELAY_SECONDS, TimeUnit.SECONDS);
        }

        return finalBarcode;
    }

    // ========== 新增：批量打印核心方法 ==========
    private void batchPrintBarcodes() {
        try {
            // 1. 从缓存中取出所有条码（批量获取）
            List<String> barcodesToPrint = new ArrayList<>();
            barcodeCache.drainTo(barcodesToPrint); // 清空缓存并获取所有元素

            if (barcodesToPrint.isEmpty()) {
                return;
            }

            // 2. 批量打印逻辑（核心：替换为你的实际打印代码）
            System.out.println("========== 开始批量打印条码 ==========");
            System.out.println("打印时间：" + new Date());
            System.out.println("本次打印条码数量：" + barcodesToPrint.size());
            System.out.println("条码列表：" + barcodesToPrint);

            // ========== 替换为你的实际打印代码 ==========
            // 示例：调用打印服务/打印机SDK
            // printService.batchPrint(barcodesToPrint);
            // 示例：生成条码图片后批量打印
            /*
            for (String barcode : barcodesToPrint) {
                BufferedImage barcodeImage = createBarCodeImage(barcode, 300, 100);
                // 调用打印机打印图片
                // printer.printImage(barcodeImage);
            }
            */

        } catch (Exception e) {
            System.err.println("批量打印条码失败：" + e.getMessage());
            e.printStackTrace();
        } finally {
            // 重置标记，允许下次提交打印任务
            isPrintTaskSubmitted.set(false);
        }
    }

    // ========== 原有方法（不变） ==========
    private static String normalizeTenantCode(String tenantCode) {
        String cleanCode = tenantCode.replaceAll("[^a-zA-Z0-9]", "");
        if (cleanCode.isEmpty()) {
            throw new IllegalArgumentException("租户Code过滤特殊字符后为空，请检查格式");
        }
        cleanCode = cleanCode.toUpperCase();
        if (cleanCode.length() >= 6) {
            return cleanCode.substring(cleanCode.length() - 6);
        } else {
            return String.format("%-6s", cleanCode).replace(' ', '0');
        }
    }

    private static String generateCheckCode(String baseCode) {
        int sum = 0;
        for (int i = 0; i < baseCode.length(); i++) {
            char c = baseCode.charAt(i);
            int coeff = (i + 1) % 2 == 0 ? 3 : 1;
            sum += (int) c * coeff;
        }
        int checkNum = sum % 100;
        return String.format("%02d", checkNum);
    }

    // ========== 新增：销毁方法（Spring容器关闭时关闭线程池） ==========
    @Override
    protected void finalize() throws Throwable {
        printExecutor.shutdown();
        printExecutor.awaitTermination(5, TimeUnit.SECONDS);
        super.finalize();
    }
}
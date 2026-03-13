package my.hive_back.common.utils;

import com.fazecast.jSerialComm.SerialPort;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * TSC TTP-244Pro USB打印机适配工具（专属TSC指令）
 */
public class TSCPrinterUtil {
    // ========== 配置项：根据你的环境调整 ==========
    private static final String PORT_NAME = "COM3"; // Windows串口（Linux：/dev/ttyUSB0）
    private static final int BAUD_RATE = 9600; // TSC 244Pro默认波特率
    // 标签纸张规格（40mm×30mm，可根据你的标签调整）
    private static final int LABEL_WIDTH_MM = 90;
    private static final int LABEL_HEIGHT_MM = 40;
    // 条码位置与尺寸（x:横向，y:纵向，单位：点；244Pro分辨率203DPI，1mm≈8点）
    private static final int BARCODE_X = 20;    // 条码左上角x坐标（≈2.5mm）
    private static final int BARCODE_Y = 10;    // 条码左上角y坐标（≈1.25mm）
    private static final int BARCODE_HEIGHT = 80; // 条码高度（≈10mm）
    private static final int BARCODE_WIDTH = 2;  // 条码宽度（1-10，2为默认）

    /**
     * 打印你的业务条码（适配TSC 244Pro）
     * @param barcode 你生成的条码（如CLTENANT026031200000189）
     * @param showText 是否显示条码下方文字（true/false）
     */
    public void printTSCBarcode(String barcode, boolean showText) {
        // 1. 初始化串口
        SerialPort serialPort = SerialPort.getCommPort(PORT_NAME);
        serialPort.setComPortParameters(BAUD_RATE, 8, 1, SerialPort.NO_PARITY);
        serialPort.setComPortTimeouts(SerialPort.TIMEOUT_WRITE_BLOCKING, 5000, 0);

        OutputStream out = null;
        try {
            // 2. 打开串口
            if (!serialPort.openPort()) {
                throw new RuntimeException("TSC打印机串口打开失败，请检查：1.USB连接 2.串口编号 3.打印机电源");
            }
            out = serialPort.getOutputStream();

            // 3. 拼接TSC指令（核心：适配244Pro的标签规格）
            StringBuilder tscCmd = new StringBuilder();
            // 3.1 初始化打印机
            tscCmd.append("! 0 200 200 210 1\r\n"); // 指令头（参数：打印速度、浓度等，默认即可）
            // 3.2 设置标签尺寸（宽×高，单位：mm）
            tscCmd.append("SIZE ").append(LABEL_WIDTH_MM).append(" mm, ").append(LABEL_HEIGHT_MM).append(" mm\r\n");
            // 3.3 设置间隙（根据你的标签纸调整，默认2mm）
            tscCmd.append("GAP 2 mm, 0 mm\r\n");
            // 3.4 清除缓冲区
            tscCmd.append("CLS\r\n");

            // 3.5 打印CODE128条码（适配你的业务条码）
            tscCmd.append("BARCODE ").append(BARCODE_X).append(",").append(BARCODE_Y)
                    .append(",CODE128,").append(BARCODE_HEIGHT)
                    .append(",").append(showText ? "1" : "0") // 1显示文字，0不显示
                    .append(",").append(BARCODE_WIDTH)
                    .append(",\"").append(barcode).append("\"\r\n");

            // 3.6 打印1张标签（可修改数字打印多张，如PRINT 5 代表打印5张）
            tscCmd.append("PRINT 1,1\r\n");
            // 3.7 结束指令
            tscCmd.append("END\r\n");

            // 4. 发送指令到打印机
            out.write(tscCmd.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            System.out.println("TSC 244Pro打印成功，条码：" + barcode);
        } catch (Exception e) {
            throw new RuntimeException("TSC打印机打印失败：" + e.getMessage(), e);
        } finally {
            // 5. 关闭资源
            try {
                if (out != null) out.close();
            } catch (Exception e) {}
            if (serialPort.isOpen()) serialPort.closePort();
        }
    }

    // 简化版：默认显示条码文字
    public void printTSCBarcode(String barcode) {
        printTSCBarcode(barcode, true);
    }

    // ========== 测试方法：直接运行验证 ==========
    public static void main(String[] args) {
        TSCPrinterUtil printer = new TSCPrinterUtil();
        // 传入你生成的条码测试
        printer.printTSCBarcode("CLTENANT026031200000189");
    }
}
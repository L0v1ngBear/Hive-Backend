package my.hive_back.common.utils;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import javax.print.*;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import java.nio.charset.StandardCharsets;

/**
 * 通用TSPL打印工具类（支持TSC/佳博等TSPL指令打印机）
 * 支持解析前端JSON格式的打印模板数据，动态生成TSPL指令
 */
@Getter
@Setter
@Component
public class TSPLPrinterUtil {
    // 默认打印机名称（可通过构造函数覆盖）
    private String printerName;
    // 标签尺寸（dot）- 可配置
    private int labelWidthDot;
    private int labelHeightDot;
    // 标签间隙（mm）
    private int gapMm;

    // 构造函数 - 默认配置（TSC TTP-244Pro）
    public TSPLPrinterUtil() {
        this("TSC TTP-244 Pro", 719, 320, 2);
    }

    // 构造函数 - 自定义配置
    public TSPLPrinterUtil(String printerName, int labelWidthDot, int labelHeightDot, int gapMm) {
        this.printerName = printerName;
        this.labelWidthDot = labelWidthDot;
        this.labelHeightDot = labelHeightDot;
        this.gapMm = gapMm;
    }

    /**
     * 核心方法：解析JSON打印模板并执行打印
     * @param printJson JSON格式的打印模板
     * 示例JSON格式：
     * {
     *   "copies": 1,          // 打印份数
     *   "direction": 1,       // 打印方向 0-正向 1-旋转
     *   "elements": [         // 打印元素列表
     *     {
     *       "type": "text",   // 元素类型：text/barcode/qrcode
     *       "x": 104,         // X坐标
     *       "y": 113,         // Y坐标
     *       "font": "ROMAN.TTF", // 字体
     *       "rotation": 0,    // 旋转角度
     *       "width": 12,      // 字体宽度
     *       "height": 12,     // 字体高度
     *       "content": "型号"  // 内容
     *     },
     *     {
     *       "type": "barcode",
     *       "x": 260,
     *       "y": 154,
     *       "codeType": "CODE128", // 条码类型
     *       "height": 100,    // 条码高度
     *       "showText": 0,    // 是否显示文字 0-否 1-是
     *       "width": 2,       // 条码宽度
     *       "content": "CLTENANT026031200000189"
     *     }
     *   ]
     * }
     */
    public void printByJson(String printJson) {
        try {
            // 1. 解析JSON数据
            JSONObject printObj = JSONObject.parseObject(printJson);
            int copies = printObj.getIntValue("copies", 1);
            int direction = printObj.getIntValue("direction", 0);
            JSONArray elements = printObj.getJSONArray("elements");

            // 2. 生成TSPL指令
            StringBuilder tsplCmd = generateTSPLCmd(direction, copies, elements);

            // 3. 执行打印
            executePrint(tsplCmd.toString(), copies);

            System.out.println("TSPL打印成功，指令：\n" + tsplCmd);
        } catch (Exception e) {
            throw new RuntimeException("TSPL打印失败：" + e.getMessage(), e);
        }
    }

    /**
     * 生成TSPL指令
     */
    private StringBuilder generateTSPLCmd(int direction, int copies, JSONArray elements) {
        StringBuilder cmd = new StringBuilder();

        // TSPL指令头（标准格式）
        cmd.append("!0202\r\n");
        // 设置标签尺寸（dot）
        cmd.append(String.format("SIZE %d dot, %d dot\r\n", labelWidthDot, labelHeightDot));
        // 设置标签间隙
        cmd.append(String.format("GAP %d mm, 0 mm\r\n", gapMm));
        // 设置打印方向
        cmd.append(String.format("DIRECTION %d\r\n", direction));
        // 清除缓冲区
        cmd.append("CLS\r\n");

        // 解析并生成打印元素指令
        if (elements != null && !elements.isEmpty()) {
            for (int i = 0; i < elements.size(); i++) {
                JSONObject element = elements.getJSONObject(i);
                String type = element.getString("type");

                switch (type.toLowerCase()) {
                    case "text":
                        cmd.append(generateTextCmd(element));
                        break;
                    case "barcode":
                        cmd.append(generateBarcodeCmd(element));
                        break;
                    case "qrcode":
                        cmd.append(generateQrCodeCmd(element));
                        break;
                    default:
                        throw new RuntimeException("不支持的打印元素类型：" + type);
                }
            }
        }

        // 打印指令
        cmd.append(String.format("PRINT %d,1\r\n", copies));

        return cmd;
    }

    /**
     * 生成文本指令
     */
    private String generateTextCmd(JSONObject textObj) {
        int x = textObj.getIntValue("x");
        int y = textObj.getIntValue("y");
        String font = textObj.getString("font");
        int rotation = textObj.getIntValue("rotation", 0);
        int width = textObj.getIntValue("width", 12);
        int height = textObj.getIntValue("height", 12);
        String content = textObj.getString("content");

        // TSPL文本指令格式：TEXT x,y,"字体",旋转,宽,高,"内容"
        return String.format("TEXT %d,%d,\"%s\",%d,%d,%d,\"%s\"\r\n",
                x, y, font, rotation, width, height, escapeContent(content));
    }

    /**
     * 生成条码指令
     */
    private String generateBarcodeCmd(JSONObject barcodeObj) {
        int x = barcodeObj.getIntValue("x");
        int y = barcodeObj.getIntValue("y");
        String codeType = barcodeObj.getString("codeType");
        int height = barcodeObj.getIntValue("height", 100);
        int showText = barcodeObj.getIntValue("showText", 0);
        int width = barcodeObj.getIntValue("width", 2);
        String content = barcodeObj.getString("content");

        // TSPL条码指令格式：BARCODE x,y,类型,高度,是否显示文字,宽度,"内容"
        return String.format("BARCODE %d,%d,%s,%d,%d,%d,\"%s\"\r\n",
                x, y, codeType, height, showText, width, escapeContent(content));
    }

    /**
     * 生成二维码指令
     */
    private String generateQrCodeCmd(JSONObject qrObj) {
        int x = qrObj.getIntValue("x");
        int y = qrObj.getIntValue("y");
        int level = qrObj.getIntValue("level", 3); // 纠错等级 1-4
        int size = qrObj.getIntValue("size", 6);   // 二维码大小 1-10
        String content = qrObj.getString("content");

        // TSPL二维码指令格式：QRCODE x,y,纠错等级,大小,"内容"
        return String.format("QRCODE %d,%d,%d,%d,\"%s\"\r\n",
                x, y, level, size, escapeContent(content));
    }

    /**
     * 执行打印操作
     */
    private void executePrint(String tsplCmd, int copies) throws Exception {
        // 查找打印机
        PrintService printService = findPrinter(printerName);
        if (printService == null) {
            throw new RuntimeException("未找到打印机：" + printerName +
                    "\n可用打印机列表：" + getAvailablePrinters());
        }

        // 创建打印任务
        DocPrintJob printJob = printService.createPrintJob();
        // 使用ISO-8859-1编码（TSPL标准）
        Doc doc = new SimpleDoc(tsplCmd.getBytes(StandardCharsets.ISO_8859_1),
                DocFlavor.BYTE_ARRAY.AUTOSENSE, null);

        // 设置打印属性
        PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
        attributes.add(new Copies(copies));

        // 执行打印
        printJob.print(doc, attributes);
    }

    /**
     * 转义内容中的特殊字符
     */
    private String escapeContent(String content) {
        if (content == null) return "";
        // 转义双引号和反斜杠
        return content.replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    /**
     * 查找打印机（模糊匹配）
     */
    private PrintService findPrinter(String printerName) {
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        for (PrintService service : services) {
            if (service.getName().contains(printerName)) {
                return service;
            }
        }
        return null;
    }

    /**
     * 获取所有可用打印机名称
     */
    public String getAvailablePrinters() {
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        StringBuilder sb = new StringBuilder();
        for (PrintService service : services) {
            sb.append(service.getName()).append("; ");
        }
        return sb.toString();
    }

    // ========== 保留原有方法（兼容旧代码） ==========
    public void printTSCBarcode(String barcode) {
        printTSCBarcode(barcode, true);
    }

    public void printTSCBarcode(String barcode, boolean showText) {
        // 构建兼容旧版本的JSON数据
        JSONObject printObj = new JSONObject();
        printObj.put("copies", 1);
        printObj.put("direction", 1);

        JSONArray elements = new JSONArray();
        // 条码元素
        JSONObject barcodeObj = new JSONObject();
        barcodeObj.put("type", "barcode");
        barcodeObj.put("x", 260);
        barcodeObj.put("y", 154);
        barcodeObj.put("codeType", "CODE128");
        barcodeObj.put("height", 100);
        barcodeObj.put("showText", showText ? 1 : 0);
        barcodeObj.put("width", 2);
        barcodeObj.put("content", barcode);
        elements.add(barcodeObj);

        printObj.put("elements", elements);

        // 调用新的打印方法
        printByJson(printObj.toJSONString());
    }

    // ========== 测试方法 ==========
    public static void main(String[] args) {
        // 1. 测试JSON打印（推荐方式）
        TSPLPrinterUtil printer = new TSPLPrinterUtil();

        // 构建测试JSON
        String testJson = "{\n" +
                "  \"copies\": 1,\n" +
                "  \"direction\": 1,\n" +
                "  \"elements\": [\n" +
                "    {\n" +
                "      \"type\": \"text\",\n" +
                "      \"x\": 104,\n" +
                "      \"y\": 113,\n" +
                "      \"font\": \"ROMAN.TTF\",\n" +
                "      \"rotation\": 0,\n" +
                "      \"width\": 12,\n" +
                "      \"height\": 12,\n" +
                "      \"content\": \"型号\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"type\": \"barcode\",\n" +
                "      \"x\": 260,\n" +
                "      \"y\": 154,\n" +
                "      \"codeType\": \"CODE128\",\n" +
                "      \"height\": 100,\n" +
                "      \"showText\": 0,\n" +
                "      \"width\": 2,\n" +
                "      \"content\": \"CLTENANT026031200000189\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"type\": \"qrcode\",\n" +
                "      \"x\": 100,\n" +
                "      \"y\": 200,\n" +
                "      \"level\": 3,\n" +
                "      \"size\": 6,\n" +
                "      \"content\": \"https://www.example.com\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        // 执行打印
        printer.printByJson(testJson);

        // 2. 测试兼容旧方法
        // printer.printTSCBarcode("1234567890");
    }

}
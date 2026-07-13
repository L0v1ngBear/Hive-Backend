package my.hive_back.module.inventory.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 小程序图片识别入库结果，返回可编辑候选后再由用户确认入库。
 */
@Data
public class InventoryImageRecognitionVO {

    private String fileName;

    private String fileUrl;

    private Long fileSize;

    private String status;

    private String message;

    private BigDecimal confidence;

    private List<Candidate> candidates;

    @Data
    public static class Candidate {

        private String barcode;

        private String modelCode;

        private BigDecimal spec;

        private BigDecimal meters;

        private BigDecimal confidence;

        private String sourceText;
    }
}

package my.hive_back.module.badproduct.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * BadProductVO 属于小程序后端坏品模块，定义出参结构。
 */
@Data
public class BadProductVO {
    private String defectiveId;
    private String orderId;
    private String type;
    private LocalDateTime createTime;
    private String creator;
    private BigDecimal quantity;
    private BigDecimal lossAmount;
    private String description;
    private String responsiblePerson;
    private String processMeasure;
    private String improvementPlan;
    private String attachmentName;
    private String attachmentUrl;
    private Long attachmentSize;
    private String status;
    private String processMethod;
    private String processRemark;
}

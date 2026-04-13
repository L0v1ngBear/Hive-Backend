package my.hive_back.module.badproduct.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    private String status;
    private String processMethod;
    private String processRemark;
}
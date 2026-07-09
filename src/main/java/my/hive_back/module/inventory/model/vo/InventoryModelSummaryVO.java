package my.hive_back.module.inventory.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InventoryModelSummaryVO {

    private String modelCode;

    private BigDecimal spec;

    private Long rollCount;

    private BigDecimal totalMeters;

    private BigDecimal remainingMeters;

    private LocalDateTime latestTime;
}

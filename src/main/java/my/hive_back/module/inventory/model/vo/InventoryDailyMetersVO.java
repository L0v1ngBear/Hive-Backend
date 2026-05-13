package my.hive_back.module.inventory.model.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryDailyMetersVO {

    private BigDecimal inMeters;

    private BigDecimal outMeters;
}

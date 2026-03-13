package my.hive_back.module.inventory.model.vo;

import lombok.Data;

@Data
public class InventoryOverViewVO {

    private Long totalRollCount;

    private Float totalMeters;

    private Float totalInMeters;

    private Float totalOutMeters;

    public InventoryOverViewVO() {
        this.totalRollCount = 0L;
        this.totalMeters = 0F;
        this.totalInMeters = 0F;
        this.totalOutMeters = 0F;
    }
}

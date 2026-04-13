package my.hive_back.module.inventory.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventoryRecordVO {
    private Long id;
    private Integer operateType;
    private LocalDateTime createTime;
    private String modelCode;
    private Float operateMeters;
    private Long operateId;
    private Float meters;
}
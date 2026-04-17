package my.hive_back.module.inventory.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
/**
 * InventoryRecordVO 属于小程序后端库存模块，定义出参结构。
 */
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

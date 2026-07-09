package my.hive_back.module.equipment.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class EquipmentRecordPageRequest {

    @Min(value = 1, message = "页码不能小于1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 200, message = "每页最多200条")
    private Integer pageSize = 10;

    private Long equipmentId;

    private String equipmentCode;

    private String result;
}

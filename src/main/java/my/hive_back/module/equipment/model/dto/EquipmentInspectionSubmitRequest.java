package my.hive_back.module.equipment.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EquipmentInspectionSubmitRequest {

    @NotBlank(message = "设备编码不能为空")
    @Size(max = 80, message = "设备编码最长80个字符")
    private String equipmentCode;

    @NotBlank(message = "巡检结果不能为空")
    @Size(max = 30, message = "巡检结果最长30个字符")
    private String inspectionResult;

    @Size(max = 500, message = "异常说明最长500个字符")
    private String abnormalDesc;

    @Size(max = 500, message = "图片地址最长500个字符")
    private String photoUrl;

    @Size(max = 500, message = "备注最长500个字符")
    private String remark;

    private LocalDateTime inspectionTime;
}

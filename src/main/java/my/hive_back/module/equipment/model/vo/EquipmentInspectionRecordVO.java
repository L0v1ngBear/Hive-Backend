package my.hive_back.module.equipment.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EquipmentInspectionRecordVO {

    private Long id;

    private Long equipmentId;

    private String equipmentCode;

    private String equipmentName;

    private String inspectionResult;

    private String abnormalDesc;

    private String photoUrl;

    private String remark;

    private Long inspectorUserId;

    private String inspectorName;

    private LocalDateTime inspectionTime;

    private LocalDateTime createTime;
}

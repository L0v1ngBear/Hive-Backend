package my.hive_back.module.equipment.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EquipmentDeviceVO {

    private Long id;

    private String equipmentCode;

    private String equipmentName;

    private String equipmentType;

    private String location;

    private String responsiblePerson;

    private Integer inspectionCycleDays;

    private LocalDateTime lastInspectionTime;

    private String status;

    private String remark;

    private String inspectionQrPayload;
}

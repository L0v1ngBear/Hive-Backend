package my.hive_back.module.equipment.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("equipment_device")
public class EquipmentDevice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantCode;

    private String equipmentCode;

    private String equipmentName;

    private String equipmentType;

    private String location;

    private String responsiblePerson;

    private Integer inspectionCycleDays;

    private LocalDateTime lastInspectionTime;

    private String status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

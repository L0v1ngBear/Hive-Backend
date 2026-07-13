package my.hive_back.module.equipment.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("equipment_inspection_record")
public class EquipmentInspectionRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantCode;

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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

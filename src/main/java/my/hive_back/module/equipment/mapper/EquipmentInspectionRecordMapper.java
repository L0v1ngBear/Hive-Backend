package my.hive_back.module.equipment.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import my.hive_back.module.equipment.model.entity.EquipmentInspectionRecord;

@InterceptorIgnore(tenantLine = "true")
public interface EquipmentInspectionRecordMapper extends BaseMapper<EquipmentInspectionRecord> {
}

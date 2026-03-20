package my.hive_back.module.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import my.hive_back.module.inventory.model.entity.ClothModelSpec;
import org.apache.ibatis.annotations.Insert;

public interface ClothModelSpecMapper extends BaseMapper<ClothModelSpec> {

    @Insert("INSERT IGNORE INTO cloth_model_spec (model_code, spec, tenant_code) " +
            "VALUES (#{modelCode}, #{spec}, #{tenantCode})")
    int insertIgnore(ClothModelSpec entity);
}

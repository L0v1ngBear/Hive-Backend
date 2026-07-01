package my.hive_back.module.inventory.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import my.hive_back.module.inventory.model.entity.ClothModelSpec;
import org.apache.ibatis.annotations.Insert;

/**
 * ClothModelSpecMapper 属于小程序后端库存模块，是数据访问类，负责与数据库交互。
 */
public interface ClothModelSpecMapper extends BaseMapper<ClothModelSpec> {

    @InterceptorIgnore(tenantLine = "true")
    @Insert("INSERT IGNORE INTO cloth_model_spec (model_code, spec, tenant_code) " +
            "VALUES (#{modelCode}, #{spec}, #{tenantCode})")
    int insertIgnore(ClothModelSpec entity);
}

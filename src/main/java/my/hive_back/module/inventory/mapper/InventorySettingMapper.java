package my.hive_back.module.inventory.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import my.hive_back.module.inventory.model.entity.InventorySetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface InventorySettingMapper extends BaseMapper<InventorySetting> {

    @Select("""
            SELECT id, tenant_code AS tenantCode, warning_threshold_meters AS warningThresholdMeters,
                   create_time AS createTime, update_time AS updateTime
            FROM inventory_setting
            WHERE tenant_code = #{tenantCode}
            LIMIT 1
            """)
    InventorySetting selectByTenantCode(@Param("tenantCode") String tenantCode);
}

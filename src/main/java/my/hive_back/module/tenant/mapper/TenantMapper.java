package my.hive_back.module.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import my.hive_back.module.tenant.model.entity.Tenant;
import org.apache.ibatis.annotations.Select;

public interface TenantMapper extends BaseMapper<Tenant> {

    @Select("select * from tenant where tenant_code = #{tenantCode}")
    Tenant selectByTenantCode(String tenantCode);
}

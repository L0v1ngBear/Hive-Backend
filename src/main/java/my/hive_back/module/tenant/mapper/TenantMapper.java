package my.hive_back.module.tenant.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import my.hive_back.module.tenant.model.entity.Tenant;
import org.apache.ibatis.annotations.Select;

/**
 * TenantMapper 属于小程序后端租户模块，是数据访问类，负责与数据库交互。
 */
public interface TenantMapper extends BaseMapper<Tenant> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("select * from tenant where tenant_code = #{tenantCode}")
    Tenant selectByTenantCode(String tenantCode);
}

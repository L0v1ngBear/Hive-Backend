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
    @Select("""
            SELECT id, tenant_code, tenant_name, tenant_type, contact_person, contact_phone,
                   password, status, package_code, package_name, subscription_status,
                   subscription_start_time, subscription_end_time, max_users,
                   max_storage_mb, feature_flags, creator,
                   create_time, update_time, deleted
            FROM tenant
            WHERE tenant_code = #{tenantCode}
            LIMIT 1
            """)
    Tenant selectByTenantCode(String tenantCode);
}

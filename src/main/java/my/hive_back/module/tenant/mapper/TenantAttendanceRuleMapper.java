package my.hive_back.module.tenant.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import my.hive_back.module.tenant.model.entity.TenantAttendanceRule;
import org.apache.ibatis.annotations.Select;

/**
 * TenantAttendanceRuleMapper 属于小程序后端租户模块，是数据访问类，负责与数据库交互。
 */
public interface TenantAttendanceRuleMapper extends BaseMapper<TenantAttendanceRule> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM tenant_attendance_rule WHERE tenant_code = #{tenantCode}")
    TenantAttendanceRule selectByTenantCode(String tenantCode);
}

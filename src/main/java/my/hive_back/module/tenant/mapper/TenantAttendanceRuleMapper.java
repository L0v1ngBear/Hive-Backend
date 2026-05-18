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
    @Select("""
            SELECT id, tenant_code, tenant_name, status, latitude, longitude, address, radius,
                   work_start_time, work_end_time, off_work_start_time, off_work_end_time,
                   over_time_start_time, over_time_end_time, late_tolerance_minutes,
                   early_tolerance_minutes, work_days, enable_gps, enable_wifi, wifi_ssid
            FROM tenant_attendance_rule
            WHERE tenant_code = #{tenantCode}
            LIMIT 1
            """)
    TenantAttendanceRule selectByTenantCode(String tenantCode);
}

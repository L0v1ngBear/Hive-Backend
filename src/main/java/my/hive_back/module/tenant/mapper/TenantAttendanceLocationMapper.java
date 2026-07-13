package my.hive_back.module.tenant.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import my.hive_back.module.tenant.model.entity.TenantAttendanceLocation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface TenantAttendanceLocationMapper extends BaseMapper<TenantAttendanceLocation> {

    @Select("""
            SELECT id, tenant_code, location_name, latitude, longitude, address, radius, status, sort_order
            FROM tenant_attendance_location
            WHERE tenant_code = #{tenantCode}
              AND status = 1
            ORDER BY sort_order ASC, id ASC
            """)
    List<TenantAttendanceLocation> selectActiveByTenantCode(@Param("tenantCode") String tenantCode);
}

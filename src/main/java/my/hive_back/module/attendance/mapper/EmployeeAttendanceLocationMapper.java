package my.hive_back.module.attendance.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import my.hive_back.module.attendance.model.entity.EmployeeAttendanceLocation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EmployeeAttendanceLocationMapper extends BaseMapper<EmployeeAttendanceLocation> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT attendance_location_id
            FROM employee_attendance_location
            WHERE tenant_code = #{tenantCode}
              AND user_id = #{userId}
            """)
    List<Long> selectLocationIds(@Param("tenantCode") String tenantCode, @Param("userId") Long userId);
}

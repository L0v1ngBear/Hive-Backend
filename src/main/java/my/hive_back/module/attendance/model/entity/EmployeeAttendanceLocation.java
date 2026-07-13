package my.hive_back.module.attendance.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("employee_attendance_location")
public class EmployeeAttendanceLocation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantCode;

    private Long userId;

    private Long attendanceLocationId;
}

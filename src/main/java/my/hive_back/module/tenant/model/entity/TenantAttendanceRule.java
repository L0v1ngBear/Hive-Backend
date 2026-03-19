package my.hive_back.module.tenant.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalTime;

@TableName("tenant_attendance_rule")
@Data
public class TenantAttendanceRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantCode;

    private String tenantName;

    private Integer status;

    private Double latitude;

    private Double longitude;

    private String address;

    private Double radius;

    private LocalTime workStartTime;

    private LocalTime workEndTime;

    private LocalTime offWorkStartTime;

    private LocalTime offWorkEndTime;

    private LocalTime overTimeStartTime;

    private LocalTime overTimeEndTime;
}

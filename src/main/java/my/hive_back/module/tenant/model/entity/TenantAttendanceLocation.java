package my.hive_back.module.tenant.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("tenant_attendance_location")
public class TenantAttendanceLocation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantCode;

    private String locationName;

    private Double latitude;

    private Double longitude;

    private String address;

    private Double radius;

    private Integer status;

    private Integer sortOrder;
}

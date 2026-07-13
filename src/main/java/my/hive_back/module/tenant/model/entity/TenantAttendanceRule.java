package my.hive_back.module.tenant.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * TenantAttendanceRule 属于小程序后端租户模块，定义持久化实体结构，用于表字段映射。
 */
@TableName
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

    /**
     * 迟到容差分钟，由管理端考勤规则维护。
     */
    private Integer lateToleranceMinutes;

    /**
     * 早退容差分钟，由管理端考勤规则维护。
     */
    private Integer earlyToleranceMinutes;

    /**
     * 工作日，1-7 对应周一到周日，多个值用英文逗号分隔。
     */
    private String workDays;

    /**
     * 是否启用 GPS 围栏，1 启用，0 关闭。
     */
    private Integer enableGps;

    /**
     * 是否启用 Wi-Fi 校验，当前作为预留字段。
     */
    private Integer enableWifi;

    /**
     * 允许打卡的 Wi-Fi 名称，当前作为预留字段。
     */
    private String wifiSsid;

    @TableField(exist = false)
    private List<TenantAttendanceLocation> locations = new ArrayList<>();
}

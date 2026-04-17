package my.hive_back.module.attendance.model.dto;

import lombok.Data;

import java.math.BigDecimal;
/**
 * AttendancePunchRequest 属于小程序后端考勤模块，定义入参结构。
 */
@Data
public class AttendancePunchRequest {

    private Double userLat;

    private Double userLng;
}

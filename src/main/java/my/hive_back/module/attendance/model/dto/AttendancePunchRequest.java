package my.hive_back.module.attendance.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AttendancePunchRequest {

    private Double userLat;

    private Double userLng;
}

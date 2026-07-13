package my.hive_back.module.attendance.model.vo;

import lombok.Data;

@Data
public class AttendanceLocationVO {

    private Long id;

    private String locationName;

    private Double latitude;

    private Double longitude;

    private String address;

    private Double radius;
}

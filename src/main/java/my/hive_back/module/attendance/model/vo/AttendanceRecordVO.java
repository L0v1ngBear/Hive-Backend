package my.hive_back.module.attendance.model.vo;

import lombok.Data;

import java.time.LocalTime;

@Data
public class AttendanceRecordVO {

    private LocalTime signInTime;

    /**
     * 上班打卡状态 (绑定 PunchStatusEnum: 正常、迟到等)
     */
    private Integer signInStatus;


    private LocalTime signOutTime;

    private Integer signOutStatus;
}

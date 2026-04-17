package my.hive_back.module.attendance.model.vo;

import lombok.Data;

import java.time.LocalTime;
/**
 * AttendanceRecordVO 属于小程序后端考勤模块，定义出参结构。
 */
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

package my.hive_back.module.attendance.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@TableName("attendance_record")
@Data
public class AttendanceRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 考勤主键：日期_用户ID (例如: 20231025_1001)
     * 保证同一天同一个用户只有一条主记录
     */
    private String punchId;

    private Long userId;

    private String tenantCode;

    // ==========================================
    //                 上班打卡数据
    // ==========================================

    /**
     * 上班打卡时间
     */
    private LocalTime signInTime;

    /**
     * 上班打卡状态 (绑定 PunchStatusEnum: 正常、迟到等)
     */
    private Integer signInStatus;

    /**
     * 上班打卡纬度
     */
    private Double signInLat;

    /**
     * 上班打卡经度
     */
    private Double signInLng;

    /**
     * 上班打卡时距离公司的距离（米）
     */
    private Double signInDistance;

    /**
     * 上班打卡位置描述（逆地理编码地址）
     */
    private String signInAddress;


    // ==========================================
    //                 下班打卡数据
    // ==========================================

    /**
     * 下班打卡时间（支持多次打卡覆盖更新）
     */
    private LocalTime signOutTime;

    /**
     * 下班打卡状态 (绑定 PunchStatusEnum: 正常、早退、加班、缺卡等)
     */
    private Integer signOutStatus;

    /**
     * 下班打卡纬度
     */
    private Double signOutLat;

    /**
     * 下班打卡经度
     */
    private Double signOutLng;

    /**
     * 下班打卡时距离公司的距离（米）
     */
    private Double signOutDistance;

    /**
     * 下班打卡位置描述（逆地理编码地址）
     */
    private String signOutAddress;

    /**
     * 打卡有效半径快照（米）
     * 存储当天的规则半径，避免后续公司修改半径影响历史记录的判定
     */
    private Double ruleRadius;

    /**
     * 记录创建时间 (首次上班打卡时生成)
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 记录更新时间 (下班打卡时更新)
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
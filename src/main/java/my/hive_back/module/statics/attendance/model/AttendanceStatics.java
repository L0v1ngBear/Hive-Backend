package my.hive_back.module.statics.attendance.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 考勤月度/日度统计实体类
 */
@TableName("attendance_statics")
@Data
public class AttendanceStatics {

    // TODO 增加唯一索引ALTER TABLE attendance_statics
    //ADD UNIQUE INDEX uk_user_tenant_date (tenant_code, user_id, statistics_date);

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户编号（多租户隔离）
     */
    private String tenantCode;

    /**
     * 用户ID（关联用户表）
     */
    private Long userId;


    /**
     * 统计日期（如果是月报，存当月第一天；如果是日报，存当日）
     */
    private LocalDate statisticsDate;

    /**
     * 应出勤天数
     */
    private Integer expectDays;

    /**
     * 实际出勤天数
     */
    private Integer actualDays;

    /**
     * 迟到次数
     */
    private Integer lateCount;

    private String userName;

    private Integer absentCount;

    /**
     * 早退次数
     */
    private Integer leaveEarlyCount;

    /**
     * 缺卡次数
     */
    private Integer missingCount;

    /**
     * 请假天数（可根据业务细分为病假、事假等）
     */
    private Float leaveDays;


    /**
     * 异常考勤天数（迟到、早退、缺卡的合集）
     */
    private Integer abnormalDays;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
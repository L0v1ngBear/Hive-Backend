package my.hive_back.module.statics.attendance.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import my.hive_back.module.statics.attendance.model.AttendanceStatics;
import org.apache.ibatis.annotations.Insert;

import java.util.List;

/**
 * AttendanceStaticsMapper 属于小程序后端统计模块，是数据访问类，负责与数据库交互。
 */
public interface AttendanceStaticsMapper extends BaseMapper<AttendanceStatics> {

    @InterceptorIgnore(tenantLine = "true")
    @Insert("<script>" +
            "INSERT INTO attendance_statics (tenant_code, user_id, user_name, statistics_date, actual_days, late_count, create_time, update_time) " +
            "VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.tenantCode}, #{item.userId}, #{item.userName}, #{item.statisticsDate}, 1, #{item.lateCount}, NOW(), NOW())" +
            "</foreach> " +
            "ON DUPLICATE KEY UPDATE " +
            "actual_days = actual_days + VALUES(actual_days), " +
            "late_count = late_count + VALUES(late_count), " +
            "update_time = NOW()" +
            "</script>")
    void upsertBatch(List<AttendanceStatics> upsertList);
}

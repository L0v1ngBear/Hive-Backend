package my.hive_back.module.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import my.hive_back.module.attendance.model.entity.AttendanceRecord;
import org.apache.ibatis.annotations.Mapper;
/**
 * AttendanceRecordMapper 属于小程序后端考勤模块，是数据访问类，负责与数据库交互。
 */
@Mapper
public interface AttendanceRecordMapper extends BaseMapper<AttendanceRecord> {
}

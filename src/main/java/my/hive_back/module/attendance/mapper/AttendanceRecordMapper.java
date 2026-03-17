package my.hive_back.module.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import my.hive_back.module.attendance.model.entity.AttendanceRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AttendanceRecordMapper extends BaseMapper<AttendanceRecord> {
}

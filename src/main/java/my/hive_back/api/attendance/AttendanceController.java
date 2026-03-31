package my.hive_back.api.attendance;

import jakarta.annotation.Resource;
import my.hive_back.common.dto.ResultDTO;
import my.hive_back.module.attendance.model.dto.AttendancePunchRequest;
import my.hive_back.module.attendance.model.entity.AttendanceRecord;
import my.hive_back.module.attendance.model.vo.AttendanceRecordVO;
import my.hive_back.module.attendance.service.AttendanceService;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/attendance")
@Validated
public class AttendanceController {

    @Resource
    private AttendanceService attendanceService;

    @PostMapping("/punch")
    public ResultDTO<String> punch(@RequestBody AttendancePunchRequest attendancePunchRequest) {
        attendanceService.punch(attendancePunchRequest);
        return ResultDTO.success("打卡成功");
    }

    @GetMapping("/select/record/{userId}")
    public ResultDTO<AttendanceRecordVO> selectRecord(@PathVariable Long userId) {
        AttendanceRecord attendanceRecord = attendanceService.selectRecord(userId);
        // 空值处理
        if (attendanceRecord == null) {
            return ResultDTO.success(new AttendanceRecordVO());
        }
        AttendanceRecordVO attendanceRecordVO = new AttendanceRecordVO();
        BeanUtils.copyProperties(attendanceRecord, attendanceRecordVO);
        return ResultDTO.success(attendanceRecordVO);
    }
}

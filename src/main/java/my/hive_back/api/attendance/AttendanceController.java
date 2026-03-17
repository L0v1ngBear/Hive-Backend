package my.hive_back.api.attendance;

import jakarta.annotation.Resource;
import my.hive_back.common.dto.ResultDTO;
import my.hive_back.module.attendance.model.dto.AttendancePunchRequest;
import my.hive_back.module.attendance.model.entity.AttendanceRecord;
import my.hive_back.module.attendance.model.vo.AttendancePunchVO;
import my.hive_back.module.attendance.service.AttendanceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController("/attendance")
@Validated
public class AttendanceController {

    @Resource
    private AttendanceService attendanceService;

    @PostMapping("/punch")
    public ResultDTO<AttendancePunchVO> punch(@RequestBody AttendancePunchRequest attendancePunchRequest) {
        AttendanceRecord attendanceRecord = attendanceService.punch(attendancePunchRequest);
    }
}

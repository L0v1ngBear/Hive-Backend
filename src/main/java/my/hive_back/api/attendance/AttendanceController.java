package my.hive_back.api.attendance;

import my.hive_back.common.dto.ResultDTO;
import my.hive_back.module.attendance.model.dto.AttendancePunchRequest;
import my.hive_back.module.attendance.model.vo.AttendancePunchVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController("/attendance")
@Validated
public class AttendanceController {

    @PostMapping("/punch")
    public ResultDTO<AttendancePunchVO> punch(@RequestBody AttendancePunchRequest attendancePunchRequest) {

    }
}

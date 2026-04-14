package my.hive_back.api.attendance;

import jakarta.annotation.Resource;
import my.hive_back.common.annotation.RequirePermission;
import my.hive_back.common.dto.Result;
import my.hive_back.module.attendance.model.dto.AttendancePunchRequest;
import my.hive_back.module.attendance.model.entity.AttendanceRecord;
import my.hive_back.module.attendance.model.vo.AttendanceRecordVO;
import my.hive_back.module.attendance.service.AttendanceService;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/attendance")
@Validated
public class AttendanceController {

    @Resource
    private AttendanceService attendanceService;

    @PostMapping("/punch")
    @RequirePermission(value = "attendance:punch", message = "您没有权限执行打卡")
    public Result<String> punch(@RequestBody AttendancePunchRequest attendancePunchRequest) {
        attendanceService.punch(attendancePunchRequest);
        return Result.success("打卡成功");
    }

    @GetMapping("/select/record/{userId}")
    @RequirePermission(value = "attendance:record:list", message = "您没有权限查看打卡记录")
    public Result<List<AttendanceRecordVO>> selectRecord(@PathVariable Long userId) {
        List<AttendanceRecord> attendanceRecords = attendanceService.selectRecord(userId);
        // 空值处理
        if (attendanceRecords == null || attendanceRecords.isEmpty()) {
            return Result.success(Collections.emptyList());
        }
        List<AttendanceRecordVO> voList = attendanceRecords.stream().map(record -> {
            AttendanceRecordVO vo = new AttendanceRecordVO();
            BeanUtils.copyProperties(record, vo);
            return vo;
        }).toList();
        return Result.success(voList);
    }
}

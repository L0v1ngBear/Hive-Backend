package my.hive_back.api.attendance;

import my.hive_back.module.tenant.TenantFeatureEnum;
import my.hive_back.module.sys.model.enums.PermissionCodeEnum;
import jakarta.annotation.Resource;
import my.hive.common.annotation.CollectLog;
import my.hive.common.annotation.RequirePermission;
import my.hive.common.dto.Result;
import my.hive_back.common.tenant.RequireTenantFeature;
import my.hive_back.module.attendance.model.dto.AttendancePunchRequest;
import my.hive_back.module.attendance.model.entity.AttendanceRecord;
import my.hive_back.module.attendance.model.vo.AttendanceRecordVO;
import my.hive_back.module.attendance.model.vo.AttendanceRuleVO;
import my.hive_back.module.attendance.service.AttendanceService;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
/**
 * AttendanceController 是小程序后端考勤入口控制类，负责接收请求并调用对应服务。
 */
@RequestMapping("/attendance")
/**
 * AttendanceController handles attendance requests for the mini-program backend and delegates to services.
 */
@RestController
@RequireTenantFeature(TenantFeatureEnum.CODE_MODULE_ATTENDANCE)
@Validated
public class AttendanceController {

    @Resource
    private AttendanceService attendanceService;

    @GetMapping("/rule")
    @RequirePermission(value = PermissionCodeEnum.CODE_ATTENDANCE_RECORD_LIST, message = "您没有权限查看考勤规则")
    public Result<AttendanceRuleVO> rule() {
        return Result.success(attendanceService.getRule());
    }

    @PostMapping("/punch")
    @RequirePermission(value = PermissionCodeEnum.CODE_ATTENDANCE_PUNCH, message = "您没有权限执行打卡")
    @CollectLog(module = "attendance", action = "punch", bizType = "attendance_record", description = "小程序考勤打卡")
    public Result<String> punch(@RequestBody AttendancePunchRequest attendancePunchRequest) {
        attendanceService.punch(attendancePunchRequest);
        return Result.success("打卡成功");
    }

    @GetMapping("/select/record/{userId}")
    @RequirePermission(value = PermissionCodeEnum.CODE_ATTENDANCE_RECORD_LIST, message = "您没有权限查看打卡记录")
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

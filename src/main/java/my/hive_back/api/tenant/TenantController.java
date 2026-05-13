package my.hive_back.api.tenant;

import my.hive_back.module.sys.model.enums.PermissionCodeEnum;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import my.hive.common.annotation.CollectLog;
import my.hive.common.annotation.RequirePermission;
import my.hive.common.dto.Result;
import my.hive_back.module.tenant.model.dto.TenantLocationAddRequest;
import my.hive_back.module.tenant.model.entity.TenantAttendanceRule;
import my.hive_back.module.tenant.service.TenantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mini-program tenant endpoints for attendance location configuration.
 */
@RestController
@RequestMapping("/tenant")
public class TenantController {

    @Resource
    private TenantService tenantService;

    @GetMapping("/attendance-location")
    @RequirePermission(value = PermissionCodeEnum.CODE_ATTENDANCE_PUNCH, message = "您没有权限查看考勤规则")
    public Result<TenantAttendanceRule> getAttendanceLocation() {
        return Result.success(tenantService.getTenantLocation());
    }

    @PostMapping("/attendance-location")
    @RequirePermission(value = PermissionCodeEnum.CODE_ATTENDANCE_ALL, message = "您没有权限设置公司打卡点")
    @CollectLog(module = "attendance", action = "save_location", bizType = "tenant_attendance_rule", description = "mini program updates company attendance location")
    public Result<TenantAttendanceRule> saveAttendanceLocation(@Valid @RequestBody TenantLocationAddRequest request) {
        return Result.success(tenantService.saveTenantLocation(request));
    }
}

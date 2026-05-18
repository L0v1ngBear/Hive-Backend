package my.hive_back.api.tenant;

import jakarta.annotation.Resource;
import my.hive.common.annotation.RequirePermission;
import my.hive.common.dto.Result;
import my.hive_back.module.sys.model.enums.PermissionCodeEnum;
import my.hive_back.module.tenant.model.entity.TenantAttendanceRule;
import my.hive_back.module.tenant.service.TenantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mini-program tenant endpoints for reading attendance location configuration.
 *
 * <p>The mini-program is no longer allowed to change company attendance coordinates.
 * Coordinates are maintained by the management backend as the single auditable source of truth.</p>
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
}

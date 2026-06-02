package my.hive_back.api.equipment;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import my.hive.common.annotation.CollectLog;
import my.hive.common.annotation.RequirePermission;
import my.hive.common.dto.PageResult;
import my.hive.common.dto.Result;
import my.hive_back.common.tenant.RequireTenantFeature;
import my.hive_back.module.equipment.model.dto.EquipmentInspectionSubmitRequest;
import my.hive_back.module.equipment.model.dto.EquipmentPageRequest;
import my.hive_back.module.equipment.model.vo.EquipmentDeviceVO;
import my.hive_back.module.equipment.model.vo.EquipmentInspectionRecordVO;
import my.hive_back.module.equipment.service.EquipmentService;
import my.hive_back.module.sys.model.enums.PermissionCodeEnum;
import my.hive_back.module.tenant.TenantFeatureEnum;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/equipment")
@RequireTenantFeature(TenantFeatureEnum.CODE_MODULE_EQUIPMENT)
@Validated
public class EquipmentController {

    @Resource
    private EquipmentService equipmentService;

    @GetMapping("/page")
    @RequirePermission(value = PermissionCodeEnum.CODE_EQUIPMENT_LIST, message = "您没有权限查看设备")
    public Result<PageResult<EquipmentDeviceVO>> page(@Valid EquipmentPageRequest request) {
        return Result.success(toPageResult(equipmentService.page(request)));
    }

    @GetMapping("/scan-target")
    @RequirePermission(value = PermissionCodeEnum.CODE_EQUIPMENT_INSPECTION_SUBMIT, message = "您没有权限执行设备巡检")
    public Result<EquipmentDeviceVO> scanTarget(String equipmentCode) {
        return Result.success(equipmentService.scanTarget(equipmentCode));
    }

    @PostMapping("/inspection/submit")
    @RequirePermission(value = PermissionCodeEnum.CODE_EQUIPMENT_INSPECTION_SUBMIT, message = "您没有权限提交设备巡检")
    @CollectLog(module = "equipment", action = "inspection_submit", bizType = "equipment", bizNo = "#request.equipmentCode", description = "小程序提交设备巡检记录")
    public Result<EquipmentInspectionRecordVO> submit(@Valid @RequestBody EquipmentInspectionSubmitRequest request) {
        return Result.success(equipmentService.submitInspection(request));
    }

    private <T> PageResult<T> toPageResult(Page<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        result.setData(page.getRecords());
        return result;
    }
}

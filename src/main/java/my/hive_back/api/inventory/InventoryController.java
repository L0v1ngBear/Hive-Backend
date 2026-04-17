package my.hive_back.api.inventory;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import my.hive_back.common.annotation.RequirePermission;
import my.hive_back.common.dto.Result;
import my.hive_back.common.exception.BusinessException;
import my.hive_back.module.inventory.model.dto.InventoryInRequest;
import my.hive_back.module.inventory.model.dto.InventoryOutRequest;
import my.hive_back.module.inventory.model.entity.Cloth;
import my.hive_back.module.inventory.model.entity.ClothModelSpec;
import my.hive_back.module.inventory.model.entity.InventoryRecord;
import my.hive_back.module.inventory.model.vo.BarCodeSearchVO;
import my.hive_back.module.inventory.model.vo.ClothInfoVO;
import my.hive_back.module.inventory.model.vo.InventoryRecordVO;
import my.hive_back.module.inventory.model.vo.ModelCodeVO;
import my.hive_back.module.inventory.service.InventoryService;
import my.hive_back.module.statics.inventory.model.vo.InventoryTrendVO;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
/**
 * InventoryController 是小程序后端库存入口控制类，负责接收请求并调用对应服务。
 */
@RestController
@RequestMapping("/inventory")
@Validated
public class InventoryController {

    @Resource
    private InventoryService inventoryService;

    @PostMapping("/cloth/in")
    @RequirePermission(value = "inventory:cloth:in", message = "您没有权限执行布匹入库")
    public Result<ClothInfoVO> inCloth(@Valid @RequestBody InventoryInRequest inventoryInRequest) {
        return Result.success(inventoryService.inCloth(inventoryInRequest));
    }

    @PostMapping("/cloth/out")
    @RequirePermission(value = "inventory:cloth:out", message = "您没有权限执行布匹出库")
    public Result<ClothInfoVO> outCloth(@Valid @RequestBody InventoryOutRequest inventoryOutRequest) {
        return Result.success(inventoryService.outCloth(inventoryOutRequest));
    }

    @GetMapping("/barCode/search")
    @RequirePermission(value = "inventory:barcode:search", message = "您没有权限查询条码")
    public Result<BarCodeSearchVO> searchBarCode(@RequestParam String barCode) {
        Cloth cloth = inventoryService.selectClothByBarCode(barCode);
        if (cloth == null) {
            throw new BusinessException("未查询到布匹信息");
        }
        BarCodeSearchVO vo = new BarCodeSearchVO();
        BeanUtils.copyProperties(cloth, vo);
        return Result.success(vo);
    }

    @GetMapping("/model/search")
    @RequirePermission(value = "inventory:model:search", message = "您没有权限查询型号规格")
    public Result<List<ModelCodeVO>> searchModelCode(@RequestParam String keyword) {
        List<ClothModelSpec> modelSpecList = inventoryService.searchModelSpec(keyword);
        List<ModelCodeVO> voList = modelSpecList.stream().map(modelSpec -> {
            ModelCodeVO vo = new ModelCodeVO();
            BeanUtils.copyProperties(modelSpec, vo);
            return vo;
        }).toList();
        return Result.success(voList);
    }

    @GetMapping("/trend")
    @RequirePermission(value = "inventory:trend", message = "您没有权限查看库存趋势")
    public Result<InventoryTrendVO> trend() {
        return Result.success(inventoryService.getLastWeekTrend());
    }

    @GetMapping("/record/recent")
    @RequirePermission(value = "inventory:record:recent", message = "您没有权限查看出入库记录")
    public Result<List<InventoryRecordVO>> recentRecord() {
        List<InventoryRecord> recordList = inventoryService.getUserRecentRecord();
        List<InventoryRecordVO> voList = recordList.stream().map(record -> {
            InventoryRecordVO vo = new InventoryRecordVO();
            BeanUtils.copyProperties(record, vo);
            vo.setOperateId(record.getId());
            return vo;
        }).toList();
        return Result.success(voList);
    }

    @GetMapping("/warning/list")
    @RequirePermission(value = "inventory:warning:list", message = "您没有权限查看库存预警")
    public Result<List<InventoryRecordVO>> warningList() {
        return Result.success(inventoryService.warningList());
    }

    @PostMapping("/cloth/out/finish")
    @RequirePermission(value = "inventory:cloth:out", message = "您没有权限提交出库打印")
    public Result<Void> finishOutboundCompat(@RequestParam String orderNo) {
        inventoryService.submitOutboundToPrint(orderNo);
        return Result.success(null);
    }

    @PostMapping("/outbound/submit-print")
    @RequirePermission(value = "inventory:cloth:out", message = "您没有权限提交出库打印")
    public Result<Void> submitOutboundToPrint(@RequestParam String orderNo) {
        inventoryService.submitOutboundToPrint(orderNo);
        return Result.success(null);
    }
}

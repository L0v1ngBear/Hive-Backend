package my.hive_back.api.inventory;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import my.hive_back.common.dto.Result;
import my.hive_back.module.inventory.model.dto.InventoryInRequest;
import my.hive_back.module.inventory.model.dto.InventoryOutRequest;
import my.hive_back.module.inventory.model.entity.Cloth;
import my.hive_back.module.inventory.model.entity.ClothModelSpec;
import my.hive_back.module.inventory.model.entity.InventoryRecord;
import my.hive_back.module.inventory.model.vo.*;
import my.hive_back.module.inventory.service.InventoryService;
import my.hive_back.module.statics.inventory.model.vo.InventoryTrendVO;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inventory")
@Validated
public class InventoryController {

    @Resource
    private InventoryService inventoryService;

    @PostMapping("/cloth/in")
    public Result<ClothInfoVO> inCloth(@Valid @RequestBody InventoryInRequest inventoryInRequest) {

        ClothInfoVO clothInfoVO = inventoryService.inCloth(inventoryInRequest);
        return Result.success(clothInfoVO);

    }

    @PostMapping("/cloth/out")
    public Result<ClothInfoVO> outCloth(@Valid @RequestBody InventoryOutRequest inventoryOutRequest) {
        ClothInfoVO clothInfoVO = inventoryService.outCloth(inventoryOutRequest);
        return Result.success(clothInfoVO);
    }


    @GetMapping("/barCode/search")
    public Result<BarCodeSearchVO> searchBarCode(@RequestParam String barCode) {
        Cloth cloth = inventoryService.selectClothByBarCode(barCode);
        BarCodeSearchVO vo = new BarCodeSearchVO();
        BeanUtils.copyProperties(cloth, vo);
        return Result.success(vo);
    }

    @GetMapping("/model/search")
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
    public Result<InventoryTrendVO> trend() {

        // 获取七天内的库存趋势数据
        InventoryTrendVO trendVO = inventoryService.getLastWeekTrend();

        return Result.success(trendVO);
    }

    @GetMapping("/record/recent")
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
    public Result<List<InventoryRecordVO>> warningList() {
        //TODO 对接ai自动分析
        return Result.success(null);
    }

    @PostMapping("/cloth/out/finish")
    public Result<Void> finishOutbound(@RequestParam String orderNo) {
        inventoryService.finishOutbound(orderNo);
        return Result.success(null);
    }
}

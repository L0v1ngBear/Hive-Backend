package my.hive_back.api.inventory;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import my.hive_back.common.dto.ResultDTO;
import my.hive_back.module.inventory.model.dto.InventoryInRequest;
import my.hive_back.module.inventory.model.dto.InventoryOutRequest;
import my.hive_back.module.inventory.model.entity.Cloth;
import my.hive_back.module.inventory.model.entity.InventoryStatics;
import my.hive_back.module.inventory.model.vo.BarCodeSearchVO;
import my.hive_back.module.inventory.model.vo.InventoryOverViewVO;
import my.hive_back.module.inventory.service.InventoryService;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.xml.transform.Result;

@RestController
@RequestMapping("/inventory")
@Validated
public class InventoryController {

    @Resource
    private InventoryService inventoryService;

    @GetMapping("/overView")
    public ResultDTO<InventoryOverViewVO> listInventory() {
        InventoryStatics inventoryStatics = inventoryService.selectInventoryStatics();
        if (inventoryStatics == null) {
            return ResultDTO.success(new InventoryOverViewVO());
        }
        InventoryOverViewVO vo = new InventoryOverViewVO();
        BeanUtils.copyProperties(inventoryStatics, vo);
        return ResultDTO.success(vo);

    }

    @PostMapping("/cloth/in")
    public ResultDTO<String> inCloth(@Valid @RequestBody InventoryInRequest inventoryInRequest) {

        String barCode = inventoryService.inCloth(inventoryInRequest);
        return ResultDTO.success(barCode);
    }

    @PostMapping("cloth/out")
    public ResultDTO<Void> outCloth(@Valid @RequestBody InventoryOutRequest inventoryOutRequest) {
        inventoryService.outCloth(inventoryOutRequest);
        return ResultDTO.success(null);
    }

//    @GetMapping("/list")
//    public ResultDTO<InventoryRecordListVO> listInventoryRecord() {

    @GetMapping("/barCode/search")
    public ResultDTO<BarCodeSearchVO> searchBarCode(@RequestParam String barCode) {
        Cloth cloth = inventoryService.selectClothByBarCode(barCode);
        BarCodeSearchVO vo = new BarCodeSearchVO();
        BeanUtils.copyProperties(cloth, vo);
        return ResultDTO.success(vo);
    }
}

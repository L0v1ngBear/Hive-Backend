package my.hive_back.api.inventory;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import my.hive_back.common.dto.ResultDTO;
import my.hive_back.module.inventory.model.dto.InventoryInRequest;
import my.hive_back.module.inventory.model.entity.InventoryStatics;
import my.hive_back.module.inventory.model.vo.InventoryOverViewVO;
import my.hive_back.module.inventory.service.InventoryService;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventory")
@Validated
public class InventoryController {

    @Resource
    private InventoryService inventoryService;

    @GetMapping("/list")
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
    public ResultDTO<Void> inCloth(@Valid @RequestBody InventoryInRequest inventoryInRequest) {

        inventoryService.inCloth(inventoryInRequest);

    }
}

package my.hive_back.api.inventory;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import my.hive_back.common.dto.ResultDTO;
import my.hive_back.module.inventory.model.dto.InventoryInRequest;
import my.hive_back.module.inventory.model.dto.InventoryOutRequest;
import my.hive_back.module.inventory.model.entity.Cloth;
import my.hive_back.module.inventory.model.entity.ClothModelSpec;
import my.hive_back.module.inventory.model.entity.InventoryStatics;
import my.hive_back.module.inventory.model.vo.BarCodeSearchVO;
import my.hive_back.module.inventory.model.vo.InventoryOverViewVO;
import my.hive_back.module.inventory.model.vo.ModelCodeVO;
import my.hive_back.module.inventory.service.InventoryService;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.xml.transform.Result;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/inventory")
@Validated
public class InventoryController {

    @Resource
    private InventoryService inventoryService;

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


    @GetMapping("/barCode/search")
    public ResultDTO<BarCodeSearchVO> searchBarCode(@RequestParam String barCode) {
        Cloth cloth = inventoryService.selectClothByBarCode(barCode);
        BarCodeSearchVO vo = new BarCodeSearchVO();
        BeanUtils.copyProperties(cloth, vo);
        return ResultDTO.success(vo);
    }

    @GetMapping("model/search")
    public ResultDTO<List<ModelCodeVO>> searchModelCode(@RequestParam String keyword) {
        List<ClothModelSpec> modelSpecList = inventoryService.searchModelSpec(keyword);
        List<ModelCodeVO> voList = modelSpecList.stream().map(modelSpec -> {
            ModelCodeVO vo = new ModelCodeVO();
            BeanUtils.copyProperties(modelSpec, vo);
            return vo;
        }).toList();
        return ResultDTO.success(voList);
    }

}

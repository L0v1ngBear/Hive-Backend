package my.hive_back.api.badproduct;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import my.hive_back.common.dto.PageResult;
import my.hive_back.common.dto.Result;
import my.hive_back.module.badproduct.model.dto.BadProductPageRequest;
import my.hive_back.module.badproduct.model.dto.BadProductProcessRequest;
import my.hive_back.module.badproduct.model.dto.BadProductSaveRequest;
import my.hive_back.module.badproduct.model.vo.BadProductVO;
import my.hive_back.module.badproduct.service.BadProductService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bad-product")
public class BadProductController {

    @Resource
    private BadProductService badProductService;

    @GetMapping("/list")
    public Result<PageResult<BadProductVO>> list(BadProductPageRequest request) {
        return Result.success(badProductService.page(request));
    }

    @PostMapping("/save")
    public Result<Void> save(@Valid @RequestBody BadProductSaveRequest request) {
        badProductService.save(request);
        return Result.success(null);
    }

    @PostMapping("/process")
    public Result<Void> process(@Valid @RequestBody BadProductProcessRequest request) {
        badProductService.process(request);
        return Result.success(null);
    }
}
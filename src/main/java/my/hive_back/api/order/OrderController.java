package my.hive_back.api.order;

import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import my.hive.common.dto.PageResult;
import my.hive.common.dto.Result;
import my.hive_back.common.tenant.RequireTenantFeature;
import my.hive_back.module.order.model.dto.BaseOrderListRequest;
import my.hive_back.module.order.service.OrderService;
import my.hive_back.module.tenant.TenantFeatureEnum;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orders")
@RequireTenantFeature(TenantFeatureEnum.CODE_MODULE_ORDER)
@Validated
public class OrderController {

    @Resource
    private OrderService orderService;

    @GetMapping("/list")
    public Result<PageResult<Map<String, Object>>> list(BaseOrderListRequest request) {
        return Result.success(orderService.page(request));
    }

    @GetMapping("/status-summary")
    public Result<Map<String, Long>> statusSummary() {
        return Result.success(orderService.countStatuses());
    }

    @GetMapping("/detail/{orderId}")
    public Result<Map<String, Object>> detail(@NotBlank @PathVariable String orderId) {
        return Result.success(orderService.detail(orderId));
    }

    @GetMapping("/status-log/{orderId}")
    public Result<List<Map<String, Object>>> statusLog(@NotBlank @PathVariable String orderId) {
        return Result.success(orderService.statusLog(orderId));
    }
}

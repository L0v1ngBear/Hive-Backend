package my.hive_back.api.order;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import my.hive.common.annotation.CollectLog;
import my.hive.common.dto.PageResult;
import my.hive.common.dto.Result;
import my.hive_back.common.tenant.RequireTenantFeature;
import my.hive_back.module.order.model.dto.BaseOrderListRequest;
import my.hive_back.module.order.model.dto.OrderFlowPrintTaskRequest;
import my.hive_back.module.order.model.dto.SalesOrderAddRequest;
import my.hive_back.module.order.model.dto.UnifiedOrderUpdateRequest;
import my.hive_back.module.order.model.vo.OrderFlowPrintTaskVO;
import my.hive_back.module.order.service.OrderService;
import my.hive_back.module.tenant.TenantFeatureEnum;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping("/{orderId}/status")
    @CollectLog(module = "order", action = "mini_update_order", bizType = "order", bizNo = "#orderId", description = "小程序更新订单")
    public Result<Map<String, Object>> update(
            @NotBlank @PathVariable String orderId,
            @Valid @RequestBody UnifiedOrderUpdateRequest request) {
        return Result.success(orderService.update(orderId, request));
    }

    @PostMapping("/{orderId}/rollback")
    @CollectLog(module = "order", action = "mini_submit_order_rollback", bizType = "order", bizNo = "#orderId", description = "小程序提交订单回退审批")
    public Result<Map<String, Object>> rollback(
            @NotBlank @PathVariable String orderId,
            @RequestBody(required = false) UnifiedOrderUpdateRequest request) {
        return Result.success(orderService.submitRollback(orderId, request));
    }

    @PostMapping("/{flowCode}/flow-advance")
    @CollectLog(module = "order", action = "mini_scan_advance_order", bizType = "order", description = "小程序扫码推进订单", recordArgs = false)
    public Result<Map<String, Object>> advanceByFlowCode(@NotBlank @PathVariable String flowCode) {
        return Result.success(orderService.advanceByFlowCode(flowCode));
    }

    @PostMapping("/add")
    @CollectLog(module = "order", action = "mini_create_order", bizType = "order", description = "小程序创建订单")
    public Result<Void> add(@Valid @RequestBody SalesOrderAddRequest request) {
        orderService.add(request);
        return Result.success(null);
    }

    @PostMapping("/flow-print-task")
    @CollectLog(module = "order", action = "mini_create_order_flow_print_task", bizType = "order", bizNo = "#request.orderId", description = "小程序创建订单流转码打印任务")
    public Result<OrderFlowPrintTaskVO> createFlowPrintTask(@Valid @RequestBody OrderFlowPrintTaskRequest request) {
        return Result.success(orderService.createFlowPrintTask(request));
    }
}

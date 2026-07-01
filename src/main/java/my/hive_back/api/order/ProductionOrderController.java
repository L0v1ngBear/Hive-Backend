package my.hive_back.api.order;

import my.hive_back.module.tenant.TenantFeatureEnum;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import my.hive.common.annotation.CollectLog;
import my.hive.common.annotation.RequirePermission;
import my.hive.common.dto.PageResult;
import my.hive.common.dto.Result;
import my.hive_back.common.tenant.RequireTenantFeature;
import my.hive_back.module.order.model.dto.ProductionOrderUpdateRequest;
import my.hive_back.module.order.model.dto.OrderFlowPrintTaskRequest;
import my.hive_back.module.order.model.dto.ProductionOrderAddRequest;
import my.hive_back.module.order.model.dto.ProductionOrderListRequest;
import my.hive_back.module.order.model.entity.ProductionOrder;
import my.hive_back.module.order.model.entity.ProductionOrderStatusLog;
import my.hive_back.module.order.model.vo.ProductionOrderVO;
import my.hive_back.module.order.model.vo.OrderFlowPrintTaskVO;
import my.hive_back.module.order.model.vo.ProductionOrderStatusLogVO;
import my.hive_back.module.order.service.OrderFlowPrintService;
import my.hive_back.module.order.service.ProductionOrderService;
import my.hive_back.module.sys.model.enums.PermissionCodeEnum;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ProductionOrderController 是小程序后端订单入口控制类，负责接收请求并调用对应服务。
 */
@RestController
@RequestMapping("/production")
@RequireTenantFeature(TenantFeatureEnum.CODE_MODULE_ORDER)
@Validated
public class ProductionOrderController {

    @Resource
    private ProductionOrderService productionOrderService;

    @Resource
    private OrderFlowPrintService orderFlowPrintService;

    /**
     * 生产订单列表查询
     * 补充：@Valid 触发复杂对象内部校验
     */
    @GetMapping("/orders/list")
    public Result<PageResult<ProductionOrderVO>> selectProductionOrder(
            ProductionOrderListRequest request) {

        IPage<ProductionOrder> page = productionOrderService.selectProductionOrder(request);
        PageResult<ProductionOrderVO> pageResultVO = new PageResult<>() {
            {
                setCurrent(page.getCurrent());
                setSize(page.getSize());
                setTotal(page.getTotal());
                setPages(page.getPages());
                setData(page.getRecords().stream()
                        .map(order -> productionOrderService.toVO(order))
                        .collect(Collectors.toList()));
            }
        };
        return Result.success(pageResultVO);
    }

    @GetMapping("/orders/status-summary")
    public Result<Map<String, Long>> productionOrderStatusSummary() {
        return Result.success(productionOrderService.countProductionOrderStatuses());
    }

    /**
     * 生产订单详情查询
     * 补充：orderId 非空 + 格式校验
     */
    @GetMapping("/orders/detail/{orderId}")
    public Result<ProductionOrderVO> getProductionOrderDetail(
            // 1. 非空校验：订单ID不能为空
            @NotBlank(message = "生产订单ID不能为空")
            @PathVariable("orderId") String orderId) {

        ProductionOrder order = productionOrderService.selectProductionOrderDetail(orderId);
        return Result.success(productionOrderService.toVO(order));
    }

    @GetMapping("/orders/status-log/{orderId}")
    public Result<List<ProductionOrderStatusLogVO>> getProductionStatusLog(@NotBlank @PathVariable String orderId) {
        List<ProductionOrderStatusLog> statusLog = productionOrderService.selectOrderStausLog(orderId);

        List<ProductionOrderStatusLogVO> logVOList = statusLog.stream().map(log ->
        {
            ProductionOrderStatusLogVO vo = new ProductionOrderStatusLogVO();
            BeanUtils.copyProperties(log, vo);
            return vo;
        }).toList();

        return Result.success(logVOList);
    }

    /**
     * 通用流转接口：支持更改订单大状态或更新生产小工序
     */
    @PostMapping("/orders/{orderId}/status")
    @CollectLog(module = "order", action = "mini_update_production_status", bizType = "production_order", bizNo = "#orderId", description = "小程序更新生产订单状态")
    public Result<ProductionOrderVO> updateOrderStatus(
            @NotBlank @PathVariable String orderId,
            @Valid @RequestBody ProductionOrderUpdateRequest request) {

        ProductionOrder order = productionOrderService.updateStatusAndProcess(orderId, request);

        return Result.success(productionOrderService.toVO(order));
    }

    @PostMapping("/orders/{orderId}/rollback")
    @CollectLog(module = "order", action = "mini_submit_production_rollback", bizType = "production_order", bizNo = "#orderId", description = "小程序提交生产订单回退审批")
    public Result<ProductionOrderVO> submitRollbackApproval(
            @NotBlank @PathVariable String orderId,
            @RequestBody(required = false) ProductionOrderUpdateRequest request) {
        ProductionOrder order = productionOrderService.submitRollbackApproval(
                orderId, request == null ? new ProductionOrderUpdateRequest() : request);
        return Result.success(productionOrderService.toVO(order));
    }

    @PostMapping("/orders/{flowCode}/flow-advance")
    @CollectLog(module = "order", action = "mini_scan_advance_production", bizType = "production_order", description = "小程序扫码推进生产订单", recordArgs = false)
    public Result<ProductionOrderVO> advanceOrderByFlowCode(@NotBlank @PathVariable String flowCode) {
        ProductionOrder order = productionOrderService.advanceByFlowCode(flowCode);
        return Result.success(productionOrderService.toVO(order));
    }

    @PostMapping("/orders/add")
    @CollectLog(module = "order", action = "mini_create_production", bizType = "production_order", description = "小程序创建生产订单")
    public Result<Void> addProductionOrder(@RequestBody ProductionOrderAddRequest request) {
        productionOrderService.addProductionOrder(request);
        return Result.success(null);
    }

    @PostMapping("/orders/flow-print-task")
    @RequirePermission(value = PermissionCodeEnum.CODE_PRODUCTION_ORDER_STATUS, message = "您没有权限生成生产订单流转码")
    @CollectLog(module = "order", action = "mini_create_production_flow_print_task", bizType = "production_order", bizNo = "#request.orderId", description = "小程序创建生产订单流转码打印任务")
    public Result<OrderFlowPrintTaskVO> createProductionFlowPrintTask(@Valid @RequestBody OrderFlowPrintTaskRequest request) {
        return Result.success(orderFlowPrintService.createProductionTask(request));
    }
}

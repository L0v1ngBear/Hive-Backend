package my.hive_back.api.order;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import my.hive_back.common.dto.PageResult;
import my.hive_back.common.dto.Result;
import my.hive_back.module.order.model.dto.ProductionOrderUpdateRequest;
import my.hive_back.module.order.model.dto.ProductionOrderAddRequest;
import my.hive_back.module.order.model.dto.ProductionOrderListRequest;
import my.hive_back.module.order.model.entity.ProductionOrder;
import my.hive_back.module.order.model.entity.ProductionOrderStatusLog;
import my.hive_back.module.order.model.vo.ProductionOrderVO;
import my.hive_back.module.order.model.vo.ProductionOrderStatusLogVO;
import my.hive_back.module.order.service.ProductionOrderService;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 生产订单控制器
 */
@RestController
@RequestMapping("/production")
@Validated
public class ProductionOrderController {

    @Resource
    private ProductionOrderService productionOrderService;

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
                setData(page.getRecords().stream().map(order -> {
                    ProductionOrderVO vo = new ProductionOrderVO();
                    BeanUtils.copyProperties(order, vo);
                    return vo;
                }).collect(Collectors.toList()));
            }
        };
        return Result.success(pageResultVO);
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
        ProductionOrderVO vo = new ProductionOrderVO();
        BeanUtils.copyProperties(order, vo);
        return Result.success(vo);
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
    public Result<ProductionOrderVO> updateOrderStatus(
            @NotBlank @PathVariable String orderId,
            @Valid @RequestBody ProductionOrderUpdateRequest request) {

        ProductionOrder order = productionOrderService.updateStatusAndProcess(orderId, request);

        ProductionOrderVO vo = new ProductionOrderVO();
        BeanUtils.copyProperties(order, vo);
        return Result.success(vo);
    }

    @PostMapping("/orders/add")
    public Result<Void> addProductionOrder(@RequestBody ProductionOrderAddRequest request) {
        productionOrderService.addProductionOrder(request);
        return Result.success(null);
    }
}
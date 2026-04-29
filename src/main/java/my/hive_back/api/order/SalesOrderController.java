package my.hive_back.api.order;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import my.hive.common.dto.PageResult;
import my.hive.common.dto.Result;
import my.hive_back.module.order.model.dto.SalesOrderAddRequest;
import my.hive_back.module.order.model.dto.SalesOrderUpdateRequest;
import my.hive_back.module.order.model.entity.SalesOrder;
import my.hive_back.module.order.model.dto.SalesOrderListRequest;
import my.hive_back.module.order.model.vo.SalesOrderStatusLogVO;
import my.hive_back.module.order.model.vo.SalesOrderVO;
import my.hive_back.module.order.service.SalesOrderService;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;
/**
 * SalesOrderController 是小程序后端订单入口控制类，负责接收请求并调用对应服务。
 */
@RestController
@RequestMapping("/sales")
@Validated
public class SalesOrderController {

    @Resource
    private SalesOrderService salesOrderService;

    /**
     * 订单列表查询：GET + 复杂对象参数（需要@Valid触发对象内部校验）
     */
    @GetMapping("/orders/list")
    public Result<PageResult<SalesOrderVO>> selectSalesOrder(SalesOrderListRequest request) {
        Page<SalesOrderVO> page = salesOrderService.selectSalesOrder(request);
        PageResult<SalesOrderVO> pageResultVo = new PageResult<>() {
            {
                setCurrent(page.getCurrent());
                setSize(page.getSize());
                setTotal(page.getTotal());
                setPages(page.getPages());
                setData(page.getRecords());
            }
        };
        return Result.success(pageResultVo);
    }

    /**
     * 订单详情：路径参数校验（非空 + 格式校验）
     */
    @GetMapping("/orders/detail/{orderId}")
    public Result<SalesOrderVO> getSalesOrderStatus(
            @NotBlank(message = "订单ID不能为空")
            @PathVariable("orderId") String orderId) {
        SalesOrderVO order = salesOrderService.getByIdandTenantId(orderId);
        if (order == null) {
            return Result.fail(404, "订单不存在");
        }
        SalesOrderVO statusVO = new SalesOrderVO();
        BeanUtils.copyProperties(order, statusVO);
        return Result.success(statusVO);
    }

    /**
     * 通用流转接口：支持更改订单大状态或更新生产小工序
     */
    @PostMapping("/orders/{orderId}/status")
    public Result<SalesOrderVO> updateOrderStatus(
            @NotBlank @PathVariable String orderId,
            @Valid @RequestBody SalesOrderUpdateRequest request) {

        SalesOrder order = salesOrderService.updateStatusAndProcess(orderId, request);

        SalesOrderVO vo = new SalesOrderVO();
        BeanUtils.copyProperties(order, vo);
        return Result.success(vo);
    }

    @GetMapping("/orders/status-log/{orderId}")
    public Result<List<SalesOrderStatusLogVO>> getSalesOrderStatusLog(@NotBlank @PathVariable String orderId) {
        List<SalesOrderStatusLogVO> logs = salesOrderService.selectSalesOrderStatusLog(orderId).stream().map(log -> {
            SalesOrderStatusLogVO vo = new SalesOrderStatusLogVO();
            BeanUtils.copyProperties(log, vo);
            return vo;
        }).collect(Collectors.toList());
        return Result.success(logs);
    }

    /**
     * 物流信息查询：路径参数校验
     */
    @GetMapping("/orders/expressInfo/{orderId}")
    public Result<SalesOrderVO> getSalesOrderExpressInfo(
            @NotBlank(message = "订单ID不能为空")
            @PathVariable("orderId") String orderId) {
        SalesOrderVO order = salesOrderService.getByIdandTenantId(orderId);
        if (order == null) {
            return Result.fail(404, "订单不存在");
        }
        // 物流接口暂不外联，当前返回订单内已维护的物流公司和单号。
        SalesOrderVO statusVO = new SalesOrderVO();
        BeanUtils.copyProperties(order, statusVO);
        return Result.success(statusVO);
    }

    @PostMapping("/orders/add")
    public Result<Void> addSalesOrder(@Valid @RequestBody SalesOrderAddRequest request) {
        salesOrderService.addSalesOrder(request);
        return Result.success(null);
    }
}

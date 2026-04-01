package my.hive_back.api.order;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import my.hive_back.common.dto.PageResultVO;
import my.hive_back.common.dto.ResultDTO;
import my.hive_back.module.order.model.dto.ProductionOrderUpdateRequest;
import my.hive_back.module.order.model.dto.SalesOrderAddRequest;
import my.hive_back.module.order.model.dto.SalesOrderUpdateRequest;
import my.hive_back.module.order.model.entity.SalesOrder;
import my.hive_back.module.order.model.dto.SalesOrderListRequest;
import my.hive_back.module.order.model.vo.SalesOrderVO;
import my.hive_back.module.order.service.SalesOrderService;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
    public ResultDTO<PageResultVO<SalesOrderVO>> selectSalesOrder(@RequestParam SalesOrderListRequest request) {
        Page<SalesOrderVO> page = salesOrderService.selectSalesOrder(request);
        PageResultVO<SalesOrderVO> pageResultVo = new PageResultVO<>() {
            {
                setCurrent(page.getCurrent());
                setSize(page.getSize());
                setTotal(page.getTotal());
                setPages(page.getPages());
                setData(page.getRecords());
            }
        };
        return ResultDTO.success(pageResultVo);
    }

    /**
     * 订单详情：路径参数校验（非空 + 格式校验）
     */
    @GetMapping("/orders/detail/{orderId}")
    public ResultDTO<SalesOrderVO> getSalesOrderStatus(
            @NotBlank(message = "订单ID不能为空")
            @PathVariable("orderId") String orderId) {
        SalesOrderVO order = salesOrderService.getByIdandTenantId(orderId);
        if (order == null) {
            return ResultDTO.fail(404, "订单不存在");
        }
        SalesOrderVO statusVO = new SalesOrderVO();
        BeanUtils.copyProperties(order, statusVO);
        return ResultDTO.success(statusVO);
    }

    /**
     * 通用流转接口：支持更改订单大状态或更新生产小工序
     */
    @PutMapping("/orders/{orderId}/status")
    public ResultDTO<SalesOrderVO> updateOrderStatus(
            @NotBlank @PathVariable String orderId,
            @Valid @RequestBody SalesOrderUpdateRequest request) {

        SalesOrder order = salesOrderService.updateStatusAndProcess(orderId, request);

        SalesOrderVO vo = new SalesOrderVO();
        BeanUtils.copyProperties(order, vo);
        return ResultDTO.success(vo);
    }

    /**
     * 物流信息查询：路径参数校验
     */
    @GetMapping("/orders/expressInfo/{orderId}")
    public ResultDTO<SalesOrderVO> getSalesOrderExpressInfo(
            @NotBlank(message = "订单ID不能为空")
            @PathVariable("orderId") String orderId) {
        SalesOrderVO order = salesOrderService.getByIdandTenantId(orderId);
        if (order == null) {
            return ResultDTO.fail(404, "订单不存在");
        }
        // TODO 对接物流信息接口
        SalesOrderVO statusVO = new SalesOrderVO();
        BeanUtils.copyProperties(order, statusVO);
        return ResultDTO.success(statusVO);
    }

    @PostMapping("/orders/add")
    public ResultDTO<Void> addSalesOrder(@Valid @RequestBody SalesOrderAddRequest request) {
        salesOrderService.addSalesOrder(request);
        return ResultDTO.success(null);
    }
}
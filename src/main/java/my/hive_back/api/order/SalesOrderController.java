package my.hive_back.api.order;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import my.hive_back.common.dto.PageResultVO;
import my.hive_back.common.dto.ResultDTO;
import my.hive_back.module.order.model.dto.SalesOrderStatusRequest;
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
        Page<SalesOrder> page = salesOrderService.selectSalesOrder(request);
        PageResultVO<SalesOrderVO> pageResultVo = new PageResultVO<>() {
            {
                setCurrent(page.getCurrent());
                setSize(page.getSize());
                setTotal(page.getTotal());
                setPages(page.getPages());
                setData(page.getRecords().stream().map(order -> {
                    SalesOrderVO vo = new SalesOrderVO();
                    BeanUtils.copyProperties(order, vo);
                    return vo;
                }).toList());
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
        SalesOrder order = salesOrderService.getByIdandTenantId(orderId);
        if (order == null) {
            return ResultDTO.fail(404, "订单不存在");
        }
        SalesOrderVO statusVO = new SalesOrderVO();
        BeanUtils.copyProperties(order, statusVO);
        return ResultDTO.success(statusVO);
    }

    /**
     * 更新订单状态：路径参数 + 请求体校验
     */
    @PostMapping("/orders/status/{orderId}")
    public ResultDTO<SalesOrderVO> updateOrderStatus(
            @NotBlank(message = "订单ID不能为空")
            @PathVariable("orderId") String orderId,
            @Valid @RequestBody SalesOrderStatusRequest request) {

        SalesOrderVO statusVO = salesOrderService.updateOrderStatus(orderId, request);
        return ResultDTO.success(statusVO);
    }

    /**
     * 物流信息查询：路径参数校验
     */
    @GetMapping("/orders/expressInfo/{orderId}")
    public ResultDTO<SalesOrderVO> getSalesOrderExpressInfo(
            @NotBlank(message = "订单ID不能为空")
            @PathVariable("orderId") String orderId) {
        SalesOrder order = salesOrderService.getByIdandTenantId(orderId);
        if (order == null) {
            return ResultDTO.fail(404, "订单不存在");
        }
        // TODO 对接物流信息接口
        SalesOrderVO statusVO = new SalesOrderVO();
        BeanUtils.copyProperties(order, statusVO);
        return ResultDTO.success(statusVO);
    }
}
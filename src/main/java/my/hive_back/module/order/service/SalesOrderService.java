package my.hive_back.module.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import my.hive_back.common.annotation.RequirePermission;
import my.hive_back.common.exception.BusinessException;
import my.hive_back.module.order.OrderStatusEnum;
import my.hive_back.module.order.mapper.SalesOrderMapper;
import my.hive_back.module.order.model.dto.SalesOrderStatusRequest;
import my.hive_back.module.order.model.entity.SalesOrder;
import my.hive_back.module.order.model.dto.SalesOrderListRequest;
import my.hive_back.module.order.model.vo.SalesOrderVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class SalesOrderService{

    @Resource
    private SalesOrderMapper salesOrderMapper;

    @RequirePermission(value = "order:sales:list", message = "您没有权限查询销售订单列表")
    public Page<SalesOrder> selectSalesOrder(SalesOrderListRequest request) {

        LambdaQueryWrapper<SalesOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SalesOrder::getStatus, request.getStatus());

        // TODO数据量大时模糊查询瓶颈
        queryWrapper.like(SalesOrder::getOrderId, request.getKeyWord());
        queryWrapper.like(SalesOrder::getCustomerName, request.getKeyWord());

        queryWrapper.orderByDesc(SalesOrder::getCreateTime);
        return salesOrderMapper.selectPage(new Page<>(request.getPageNum(), request.getPageSize()), queryWrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    @RequirePermission(value = "order:sales:update", message = "您没有权限更新销售订单状态")
    public SalesOrderVO updateOrderStatus(String orderId, SalesOrderStatusRequest request) {

        // 校验已发货订单是否提供物流信息
        if (OrderStatusEnum.SHIPPED.getName().equals(request.getStatus())) {
            if (request.getExpressInfo() == null ||
                    request.getExpressInfo().getExpressCompany().isBlank() ||
                    request.getExpressInfo().getExpressNo().isBlank()) {
                throw new BusinessException(401, "已发货订单必须提供物流信息");
            }
        }

        LambdaQueryWrapper<SalesOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SalesOrder::getOrderId, orderId);

        // 查询订单是否存在
        SalesOrder order = salesOrderMapper.selectOne(queryWrapper);

        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }

        // 校验订单状态转换是否有效
        String oldStatus = order.getStatus();
        String newStatus = request.getStatus();
        if (!OrderStatusEnum.canFlowTo(oldStatus, newStatus)) {
            throw new BusinessException(401, "订单状态转换无效");
        }

        // 更新订单状态
        // 乐观锁控制
        order.setStatus(newStatus);
        // 仅当状态为已发货时，才赋值物流信息
        if (OrderStatusEnum.SHIPPED.getName().equals(newStatus)) {
            order.setExpressCompany(request.getExpressInfo().getExpressCompany());
            order.setExpressNo(request.getExpressInfo().getExpressNo());
        } else {
            // 非发货状态：可清空物流信息（或根据业务需求处理）
            order.setExpressCompany(null);
            order.setExpressNo(null);
        }

        salesOrderMapper.updateStatus(order, oldStatus);

        // copy属性
        SalesOrderVO vo = new SalesOrderVO();
        BeanUtils.copyProperties(order, vo);

        // 返回更新后的订单状态
        return vo;
    }

    @RequirePermission(value = "order:sales:detail", message = "您没有权限查询销售订单详情")
    public SalesOrder getByIdandTenantId(String orderId) {
        return salesOrderMapper.selectByOrderId(orderId);
    }
}

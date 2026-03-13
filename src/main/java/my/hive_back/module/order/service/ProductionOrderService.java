package my.hive_back.module.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import my.hive_back.common.annotation.RequirePermission;
import my.hive_back.common.exception.BusinessException;
import my.hive_back.module.order.ProcessEnum;
import my.hive_back.module.order.mapper.ProductionOrderMapper;
import my.hive_back.module.order.mapper.ProductionOrderStatusLogMapper;
import my.hive_back.module.order.model.dto.ProductionOrderListRequest;
import my.hive_back.module.order.model.entity.ProductionOrder;
import my.hive_back.module.order.model.entity.ProductionOrderStatusLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductionOrderService {

    @Resource
    private ProductionOrderMapper productionOrderMapper;

    @Resource
    private ProductionOrderStatusLogMapper statusLogMapper;

    /**
     * 查询生产订单列表
     * @param request
     * @return
     */
    @RequirePermission(value = "order:production:list", message = "您没有权限查询生产订单列表")
    public Page<ProductionOrder> selectProductionOrder(ProductionOrderListRequest request) {

        LambdaQueryWrapper<ProductionOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ProductionOrder::getStatus, request.getStatus());
        queryWrapper.like(ProductionOrder::getOrderId, request.getKeyWord());
        queryWrapper.like(ProductionOrder::getCustomerName, request.getKeyWord());
        queryWrapper.like(ProductionOrder::getProjectName, request.getKeyWord());
        queryWrapper.orderByDesc(ProductionOrder::getOrderId);

        return productionOrderMapper.selectPage(new Page<>(request.getPageNum(), request.getPageSize()), queryWrapper);
    }

    /**
     * 查询生产订单详情
     * @param orderId
     * @return
     */
    @RequirePermission(value = "order:production:detail", message = "您没有权限查询生产订单详情")
    public ProductionOrder selectProductionOrderDetail(String orderId) {

        ProductionOrder productionOrder = productionOrderMapper.selectByOrderId(orderId);

        if (productionOrder == null) {
            throw new BusinessException(400, "订单不存在");
        }

        return productionOrder;
    }
    /**
     * 查询生产订单状态变更日志
     * @param orderId
     * @return
     */
    @RequirePermission(value = "order:production:log", message = "您没有权限查询生产订单状态变更日志")
    public List<ProductionOrderStatusLog> selectOrderStausLog(@NotBlank String orderId) {

        LambdaQueryWrapper<ProductionOrderStatusLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ProductionOrderStatusLog::getOrderId, orderId);
        queryWrapper.orderByAsc(ProductionOrderStatusLog::getCreateTime);
        return statusLogMapper.selectList(queryWrapper);
    }

    @RequirePermission(value = "order:production:process", message = "您没有权限处理生产订单")
    @Transactional(rollbackFor = Exception.class)
    public ProductionOrder processProductionOrder(String orderId, Integer process) {

        if (ProcessEnum.getByCode(process) == null) {
            throw new BusinessException(400, "无效的生产工序编码");
        }

        // for update 悲观锁，确保在更新状态时不会被其他事务修改
        ProductionOrder productionOrder = productionOrderMapper.selectByOrderId(orderId);

        if (productionOrder == null) {
            throw new BusinessException(400, "订单不存在");
        }

        productionOrder.setProcess(process);
        productionOrderMapper.updateById(productionOrder);

        ProductionOrderStatusLog statusLog = new ProductionOrderStatusLog();
        statusLog.setOrderId(orderId);
        statusLog.setOldStatus(productionOrder.getStatus());
        statusLog.setNewStatus(ProcessEnum.getByCode(process).getName());

        statusLogMapper.insert(statusLog);

        return productionOrder;
    }
}

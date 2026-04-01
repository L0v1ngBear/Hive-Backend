package my.hive_back.module.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import my.hive_back.common.annotation.RequirePermission;
import my.hive_back.common.context.TenantPermissionContext;
import my.hive_back.common.exception.BusinessException;
import my.hive_back.module.order.OrderStatusEnum;
import my.hive_back.module.order.ProcessEnum;
import my.hive_back.module.order.mapper.ProductionOrderMapper;
import my.hive_back.module.order.mapper.ProductionOrderStatusLogMapper;
import my.hive_back.module.order.model.dto.ProductionOrderUpdateRequest;
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

    @Transactional(rollbackFor = Exception.class)
    public ProductionOrder updateStatusAndProcess(String orderId, ProductionOrderUpdateRequest request) {
        // 1. 查询当前订单
        ProductionOrder order = productionOrderMapper.selectOne(new LambdaQueryWrapper<ProductionOrder>()
                .eq(ProductionOrder::getOrderId, orderId));

        if (order == null) {
            throw new BusinessException(400, "订单不存在");
        }

        // --- 并发控制快照 ---
        // 记录修改前的原始状态和工序，用于最后的 CAS (Compare-And-Swap) 并发安全校验
        String oldStatus = order.getStatus();
        Integer oldProcess = order.getProcess();

        // 2. 逻辑分支 A：如果传入了 status，尝试切换大状态
        if (StringUtils.isNotBlank(request.getStatus())) {
            // 如果是从其他状态切换到 'producing'，初始化工序为 0 (整经)
            if (OrderStatusEnum.PRODUCING.getName().equals(request.getStatus())
                    && !OrderStatusEnum.PRODUCING.getName().equals(order.getStatus())) {
                order.setProcess(0);
            }
            // 如果切出 'producing' 状态（如发货了），可以清空或保留工序记录
            else if (!OrderStatusEnum.PRODUCING.getName().equals(request.getStatus())) {
                order.setProcess(null); // 根据业务需求决定是否清空
            }
            order.setStatus(request.getStatus());
        }

        // 3. 逻辑分支 B：如果当前（或切换后）是生产中，且传入了 process，更新小工序
        if (OrderStatusEnum.PRODUCING.getName().equals(order.getStatus()) && request.getProcess() != null) {
            order.setProcess(request.getProcess());
        }

        // 记录操作人
        order.setUpdater(TenantPermissionContext.getUserId());

        // 4. 并发安全更新（核心改造点）
        // 构建基于快照的更新条件：只有当数据库里的状态和工序与第一步查出来的一致时，才允许更新
        LambdaUpdateWrapper<ProductionOrder> updateWrapper = new LambdaUpdateWrapper<ProductionOrder>()
                .eq(ProductionOrder::getOrderId, orderId);

        // 校验旧状态
        if (oldStatus == null) {
            updateWrapper.isNull(ProductionOrder::getStatus);
        } else {
            updateWrapper.eq(ProductionOrder::getStatus, oldStatus);
        }

        // 校验旧工序
        if (oldProcess == null) {
            updateWrapper.isNull(ProductionOrder::getProcess);
        } else {
            updateWrapper.eq(ProductionOrder::getProcess, oldProcess);
        }

        // 执行更新，MyBatis-Plus 的 update 方法会返回受影响的行数
        int updatedRows = productionOrderMapper.update(order, updateWrapper);

        // 如果受影响行数为 0，说明在查询和更新的时间差内，数据被其他线程改动了
        if (updatedRows == 0) {
            throw new BusinessException(409, "订单状态已被其他人修改，操作失败，请刷新后重试");
        }

        return order;
    }
}

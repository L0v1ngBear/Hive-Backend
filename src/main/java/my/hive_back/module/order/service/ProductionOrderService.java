package my.hive_back.module.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import my.hive.common.annotation.RequirePermission;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive_back.common.utils.CodeGeneratorUtil;
import my.hive_back.module.order.model.dto.ProductionOrderAddRequest;
import my.hive_back.module.order.model.dto.ProductionOrderListRequest;
import my.hive_back.module.order.model.dto.ProductionOrderUpdateRequest;
import my.hive_back.module.order.model.entity.ProductionOrder;
import my.hive_back.module.order.model.entity.ProductionOrderStatusLog;
import my.hive_back.module.order.mapper.ProductionOrderMapper;
import my.hive_back.module.order.mapper.ProductionOrderStatusLogMapper;
import my.hive_back.module.user.mapper.UserMapper;
import my.hive_back.module.user.model.entity.User;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
/**
 * ProductionOrderService 属于小程序后端订单模块，实现核心业务编排与规则逻辑。
 */
@Service
public class ProductionOrderService {

    private static final String STATUS_PRODUCING = "producing";
    private static final Set<String> VALID_STATUS = Set.of(
            "pending_confirm", "pending_material", "producing", "pending_ship", "shipped", "completed"
    );

    @Resource
    private ProductionOrderMapper productionOrderMapper;

    @Resource
    private ProductionOrderStatusLogMapper statusLogMapper;

    @Resource
    private CodeGeneratorUtil codeGeneratorUtil;

    @Resource
    private UserMapper userMapper;

    @RequirePermission(value = "order:production:list", message = "您没有权限查询生产订单列表")
    public Page<ProductionOrder> selectProductionOrder(ProductionOrderListRequest request) {
        LambdaQueryWrapper<ProductionOrder> queryWrapper = new LambdaQueryWrapper<>();

        if (StringUtils.isNotBlank(request.getStatus())) {
            queryWrapper.eq(ProductionOrder::getStatus, request.getStatus());
        }

        if (StringUtils.isNotBlank(request.getKeyWord())) {
            String keyWord = request.getKeyWord().trim();
            queryWrapper.and(wrapper -> wrapper
                    .like(ProductionOrder::getOrderId, keyWord)
                    .or()
                    .like(ProductionOrder::getCustomerName, keyWord)
                    .or()
                    .like(ProductionOrder::getProjectName, keyWord)
                    .or()
                    .like(ProductionOrder::getModelCode, keyWord));
        }

        queryWrapper.orderByDesc(ProductionOrder::getOrderId);
        return productionOrderMapper.selectPage(new Page<>(request.getPageNum(), request.getPageSize()), queryWrapper);
    }

    @RequirePermission(value = "order:production:detail", message = "您没有权限查询生产订单详情")
    public ProductionOrder selectProductionOrderDetail(String orderId) {
        ProductionOrder productionOrder = productionOrderMapper.selectOne(new LambdaQueryWrapper<ProductionOrder>()
                .eq(ProductionOrder::getOrderId, orderId));

        if (productionOrder == null) {
            throw new BusinessException(400, "订单不存在");
        }

        return productionOrder;
    }

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
        ProductionOrderUpdateRequest request = new ProductionOrderUpdateRequest();
        request.setProcess(process);
        request.setOperateType("process_change");
        request.setRemark("更新生产工序");
        return updateStatusAndProcess(orderId, request);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductionOrder updateStatusAndProcess(String orderId, ProductionOrderUpdateRequest request) {
        ProductionOrder order = productionOrderMapper.selectOne(new LambdaQueryWrapper<ProductionOrder>()
                .eq(ProductionOrder::getOrderId, orderId));

        if (order == null) {
            throw new BusinessException(400, "订单不存在");
        }

        String oldStatus = order.getStatus();
        Integer oldProcess = order.getProcess();

        if (StringUtils.isNotBlank(request.getStatus())) {
            String targetStatus = request.getStatus().trim();
            if (!VALID_STATUS.contains(targetStatus)) {
                throw new BusinessException(400, "无效的订单状态");
            }

            if (STATUS_PRODUCING.equals(targetStatus) && !STATUS_PRODUCING.equals(order.getStatus())) {
                order.setProcess(0);
            } else if (!STATUS_PRODUCING.equals(targetStatus)) {
                order.setProcess(null);
            }
            order.setStatus(targetStatus);
        }

        if (STATUS_PRODUCING.equals(order.getStatus()) && request.getProcess() != null) {
            order.setProcess(request.getProcess());
        }

        order.setUpdater(resolveCurrentUserIdText());

        LambdaUpdateWrapper<ProductionOrder> updateWrapper = new LambdaUpdateWrapper<ProductionOrder>()
                .eq(ProductionOrder::getOrderId, orderId);

        if (oldStatus == null) {
            updateWrapper.isNull(ProductionOrder::getStatus);
        } else {
            updateWrapper.eq(ProductionOrder::getStatus, oldStatus);
        }

        if (oldProcess == null) {
            updateWrapper.isNull(ProductionOrder::getProcess);
        } else {
            updateWrapper.eq(ProductionOrder::getProcess, oldProcess);
        }

        updateWrapper.set(ProductionOrder::getStatus, order.getStatus());
        updateWrapper.set(ProductionOrder::getProcess, order.getProcess());
        updateWrapper.set(ProductionOrder::getUpdater, order.getUpdater());
        updateWrapper.set(ProductionOrder::getUpdateTime, LocalDateTime.now());

        int updatedRows = productionOrderMapper.update(null, updateWrapper);
        if (updatedRows == 0) {
            throw new BusinessException(409, "订单状态已被其他人修改，操作失败，请刷新后重试");
        }

        if (!Objects.equals(oldStatus, order.getStatus()) || !Objects.equals(oldProcess, order.getProcess())) {
            insertStatusLog(orderId, oldStatus, oldProcess, order.getStatus(), order.getProcess(), request);
        }

        return order;
    }

    @Transactional(rollbackFor = Exception.class)
    public void addProductionOrder(ProductionOrderAddRequest request) {
        this.addProductionOrder(request, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void addProductionOrder(ProductionOrderAddRequest request, String salesOrderId) {
        ProductionOrder productionOrder = new ProductionOrder();
        BeanUtils.copyProperties(request, productionOrder);
        productionOrder.setOrderId(codeGeneratorUtil.generateProductionOrderCode());
        productionOrder.setTenantCode(TenantPermissionContext.getTenantCode());
        productionOrder.setCreator(resolveCurrentUserIdText());
        productionOrder.setUpdater(resolveCurrentUserIdText());

        if (StringUtils.isNotBlank(salesOrderId)) {
            productionOrder.setSalesOrderId(salesOrderId);
        }

        productionOrderMapper.insert(productionOrder);
    }

    private void insertStatusLog(String orderId,
                                 String oldStatus,
                                 Integer oldProcess,
                                 String newStatus,
                                 Integer newProcess,
                                 ProductionOrderUpdateRequest request) {
        ProductionOrderStatusLog statusLog = new ProductionOrderStatusLog();
        statusLog.setTenantCode(TenantPermissionContext.getTenantCode());
        statusLog.setOrderId(orderId);
        statusLog.setOldStatus(buildStatusText(oldStatus, oldProcess));
        statusLog.setNewStatus(buildStatusText(newStatus, newProcess));
        statusLog.setOperateType(resolveOperateType(oldStatus, newStatus, oldProcess, newProcess, request));
        statusLog.setRemark(StringUtils.isNotBlank(request.getRemark()) ? request.getRemark() : buildDefaultRemark(oldStatus, oldProcess, newStatus, newProcess));
        statusLog.setOperator(String.valueOf(TenantPermissionContext.getUserId()));
        statusLog.setOperatorName(resolveCurrentUserName());
        statusLog.setCreateTime(LocalDateTime.now());
        statusLogMapper.insert(statusLog);
    }

    private String resolveOperateType(String oldStatus,
                                      String newStatus,
                                      Integer oldProcess,
                                      Integer newProcess,
                                      ProductionOrderUpdateRequest request) {
        if (StringUtils.isNotBlank(request.getOperateType())) {
            return request.getOperateType();
        }
        if (!Objects.equals(oldStatus, newStatus)) {
            return "status_change";
        }
        if (!Objects.equals(oldProcess, newProcess)) {
            return "process_change";
        }
        return "update";
    }

    private String buildDefaultRemark(String oldStatus, Integer oldProcess, String newStatus, Integer newProcess) {
        return "由「" + buildStatusText(oldStatus, oldProcess) + "」更新为「" + buildStatusText(newStatus, newProcess) + "」";
    }

    private String resolveCurrentUserName() {
        Long userId = TenantPermissionContext.getUserId();
        if (userId == null) {
            return "系统";
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getTenantCode, TenantPermissionContext.getTenantCode())
                .eq(User::getId, userId)
                .last("LIMIT 1"));
        if (user != null && StringUtils.isNotBlank(user.getName())) {
            return user.getName();
        }
        return String.valueOf(userId);
    }

    private String resolveCurrentUserIdText() {
        Long userId = TenantPermissionContext.getUserId();
        return userId == null ? "system" : String.valueOf(userId);
    }

    private String buildStatusText(String status, Integer process) {
        if (!StringUtils.isNotBlank(status)) {
            return "未设置";
        }
        if (STATUS_PRODUCING.equals(status) && process != null) {
            return statusLabel(status) + "-" + processLabel(process);
        }
        return statusLabel(status);
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "pending_confirm" -> "待确认";
            case "pending_material" -> "备料中";
            case "producing" -> "生产中";
            case "pending_ship" -> "待发货";
            case "shipped" -> "已发货";
            case "completed" -> "已完成";
            default -> status;
        };
    }

    private String processLabel(Integer process) {
        return switch (process) {
            case 0 -> "整经";
            case 1 -> "浆纱";
            case 2 -> "织造";
            case 3 -> "验布";
            case 4 -> "卷布";
            default -> "未知工序";
        };
    }
}

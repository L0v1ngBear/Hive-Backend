package my.hive_back.module.order.service;

import my.hive_back.module.sys.model.enums.PermissionCodeEnum;
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
import my.hive_back.module.order.OrderOperateTypeEnum;
import my.hive_back.module.order.OrderStatusEnum;
import my.hive_back.module.order.ProcessEnum;
import my.hive_back.module.order.model.dto.ProductionOrderAddRequest;
import my.hive_back.module.order.model.dto.ProductionOrderListRequest;
import my.hive_back.module.order.model.dto.ProductionOrderUpdateRequest;
import my.hive_back.module.order.model.entity.ProductionOrder;
import my.hive_back.module.order.model.entity.ProductionOrderStatusLog;
import my.hive_back.module.order.mapper.ProductionOrderMapper;
import my.hive_back.module.order.mapper.ProductionOrderStatusLogMapper;
import my.hive_back.module.user.mapper.UserMapper;
import my.hive_back.module.user.model.entity.User;
import my.hive_back.module.wechat.service.WechatSubscribeNotificationService;
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

    private static final long DEFAULT_PAGE_NUM = 1L;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 200L;
    private static final String STATUS_PRODUCING = OrderStatusEnum.PRODUCING.getCode();
    private static final Set<String> VALID_STATUS = Set.of(
            OrderStatusEnum.PENDING_CONFIRM.getCode(),
            OrderStatusEnum.PENDING_MATERIAL.getCode(),
            OrderStatusEnum.PRODUCING.getCode(),
            OrderStatusEnum.PENDING_SHIP.getCode(),
            OrderStatusEnum.SHIPPED.getCode(),
            OrderStatusEnum.COMPLETED.getCode()
    );

    @Resource
    private ProductionOrderMapper productionOrderMapper;

    @Resource
    private ProductionOrderStatusLogMapper statusLogMapper;

    @Resource
    private CodeGeneratorUtil codeGeneratorUtil;

    @Resource
    private UserMapper userMapper;

    @Resource
    private WechatSubscribeNotificationService wechatSubscribeNotificationService;

    @RequirePermission(value = PermissionCodeEnum.CODE_PRODUCTION_ORDER_LIST, message = "您没有权限查询生产订单列表")
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
        return productionOrderMapper.selectPage(
                new Page<>(safePageNum(request.getPageNum()), safePageSize(request.getPageSize())),
                queryWrapper
        );
    }

    private long safePageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? DEFAULT_PAGE_NUM : pageNum;
    }

    private long safePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    @RequirePermission(value = PermissionCodeEnum.CODE_PRODUCTION_ORDER_DETAIL, message = "您没有权限查询生产订单详情")
    public ProductionOrder selectProductionOrderDetail(String orderId) {
        ProductionOrder productionOrder = productionOrderMapper.selectOne(new LambdaQueryWrapper<ProductionOrder>()
                .eq(ProductionOrder::getOrderId, orderId));

        if (productionOrder == null) {
            throw new BusinessException(400, "订单不存在");
        }

        return productionOrder;
    }

    @RequirePermission(value = PermissionCodeEnum.CODE_PRODUCTION_ORDER_LOG, message = "您没有权限查询生产订单状态变更日志")
    public List<ProductionOrderStatusLog> selectOrderStausLog(@NotBlank String orderId) {
        LambdaQueryWrapper<ProductionOrderStatusLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ProductionOrderStatusLog::getOrderId, orderId);
        queryWrapper.orderByAsc(ProductionOrderStatusLog::getCreateTime);
        return statusLogMapper.selectList(queryWrapper);
    }

    @RequirePermission(value = PermissionCodeEnum.CODE_PRODUCTION_ORDER_STATUS, message = "您没有权限处理生产订单")
    @Transactional(rollbackFor = Exception.class)
    public ProductionOrder processProductionOrder(String orderId, Integer process) {
        ProductionOrderUpdateRequest request = new ProductionOrderUpdateRequest();
        request.setProcess(process);
        request.setOperateType(OrderOperateTypeEnum.PROCESS_CHANGE.getCode());
        request.setRemark("更新生产工序");
        return updateStatusAndProcess(orderId, request);
    }

    @Transactional(rollbackFor = Exception.class)
    @RequirePermission(value = PermissionCodeEnum.CODE_PRODUCTION_ORDER_STATUS, message = "您没有权限更新生产订单状态")
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
            notifyProductionOrderChanged(order, oldStatus, oldProcess);
        }

        return order;
    }

    @Transactional(rollbackFor = Exception.class)
    @RequirePermission(value = PermissionCodeEnum.CODE_PRODUCTION_ORDER_STATUS, message = "您没有权限添加生产订单")
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
            return OrderOperateTypeEnum.STATUS_CHANGE.getCode();
        }
        if (!Objects.equals(oldProcess, newProcess)) {
            return OrderOperateTypeEnum.PROCESS_CHANGE.getCode();
        }
        return OrderOperateTypeEnum.UPDATE.getCode();
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

    private void notifyProductionOrderChanged(ProductionOrder order, String oldStatus, Integer oldProcess) {
        Long creatorId = parseUserId(order.getCreator());
        Long currentUserId = TenantPermissionContext.getUserId();
        if (creatorId == null || creatorId.equals(currentUserId)) {
            return;
        }
        wechatSubscribeNotificationService.sendTodoAfterCommit(
                creatorId,
                "生产订单状态更新",
                order.getOrderId() + "：" + buildStatusText(oldStatus, oldProcess) + " → " + buildStatusText(order.getStatus(), order.getProcess()),
                "/pages/orderDetail/orderDetail?orderId=" + order.getOrderId()
        );
    }

    private Long parseUserId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
        try {
            return OrderStatusEnum.getByCode(status).getName();
        } catch (IllegalArgumentException ex) {
            return status;
        }
    }

    private String processLabel(Integer process) {
        try {
            return ProcessEnum.getByCode(process).getName();
        } catch (IllegalArgumentException ex) {
            return "\u672a\u77e5\u5de5\u5e8f";
        }
    }

}

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
import my.hive.common.order.OrderFlowCodeUtil;
import my.hive_back.common.utils.CodeGeneratorUtil;
import my.hive_back.module.order.OrderCategoryEnum;
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
import my.hive_back.module.order.model.vo.ProductionOrderVO;
import my.hive_back.module.order.model.vo.ProductionProcessStepVO;
import my.hive_back.module.user.mapper.UserMapper;
import my.hive_back.module.user.model.entity.User;
import my.hive_back.module.wechat.service.WechatSubscribeNotificationService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final String STATUS_PENDING_SHIP = OrderStatusEnum.PENDING_SHIP.getCode();
    private static final Set<String> VALID_STATUS = Set.of(
            OrderStatusEnum.PENDING_CONFIRM.getCode(),
            OrderStatusEnum.PENDING_MATERIAL.getCode(),
            OrderStatusEnum.PRODUCING.getCode(),
            OrderStatusEnum.PENDING_SHIP.getCode(),
            OrderStatusEnum.SHIPPED.getCode(),
            OrderStatusEnum.COMPLETED.getCode()
    );
    private static final List<String> PRODUCTION_STATUS_CODES = List.of(
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

    @Value("${ORDER_FLOW_CODE_SECRET:${AUTH_TOKEN_SECRET:hive-local-order-flow-secret}}")
    private String orderFlowCodeSecret;

    public ProductionOrderVO toVO(ProductionOrder order) {
        ProductionOrderVO vo = new ProductionOrderVO();
        if (order == null) {
            return vo;
        }
        BeanUtils.copyProperties(order, vo);
        fillProductionProcessView(vo, order.getStatus(), order.getProcess());
        return vo;
    }

    @RequirePermission(value = PermissionCodeEnum.CODE_PRODUCTION_ORDER_LIST, message = "您没有权限查询生产订单列表")
    public Page<ProductionOrder> selectProductionOrder(ProductionOrderListRequest request) {
        LambdaQueryWrapper<ProductionOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ProductionOrder::getTenantCode, TenantPermissionContext.getTenantCode());

        if (StringUtils.isNotBlank(request.getStatus())) {
            queryWrapper.eq(ProductionOrder::getStatus, request.getStatus());
        }
        if (StringUtils.isNotBlank(request.getOrderCategory())) {
            queryWrapper.eq(ProductionOrder::getOrderCategory, OrderCategoryEnum.normalize(request.getOrderCategory()));
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
                    .like(ProductionOrder::getBrandName, keyWord)
                    .or()
                    .like(ProductionOrder::getModelCode, keyWord));
        }

        queryWrapper.orderByDesc(ProductionOrder::getOrderId);
        return productionOrderMapper.selectPage(
                new Page<>(safePageNum(request.getPageNum()), safePageSize(request.getPageSize())),
                queryWrapper
        );
    }

    @RequirePermission(value = PermissionCodeEnum.CODE_PRODUCTION_ORDER_LIST, message = "您没有权限查询生产订单统计")
    public Map<String, Long> countProductionOrderStatuses() {
        String tenantCode = TenantPermissionContext.getTenantCode();
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("total", safeCount(productionOrderMapper.selectCount(new LambdaQueryWrapper<ProductionOrder>()
                .eq(ProductionOrder::getTenantCode, tenantCode))));
        for (String status : PRODUCTION_STATUS_CODES) {
            result.put(status, safeCount(productionOrderMapper.selectCount(new LambdaQueryWrapper<ProductionOrder>()
                    .eq(ProductionOrder::getTenantCode, tenantCode)
                    .eq(ProductionOrder::getStatus, status))));
        }
        return result;
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

    private long safeCount(Long count) {
        return count == null ? 0L : count;
    }

    @RequirePermission(value = PermissionCodeEnum.CODE_PRODUCTION_ORDER_DETAIL, message = "您没有权限查询生产订单详情")
    public ProductionOrder selectProductionOrderDetail(String orderId) {
        ProductionOrder productionOrder = productionOrderMapper.selectOne(new LambdaQueryWrapper<ProductionOrder>()
                .eq(ProductionOrder::getTenantCode, TenantPermissionContext.getTenantCode())
                .eq(ProductionOrder::getOrderId, orderId));

        if (productionOrder == null) {
            throw new BusinessException(400, "订单不存在");
        }

        return productionOrder;
    }

    @RequirePermission(value = PermissionCodeEnum.CODE_PRODUCTION_ORDER_LOG, message = "您没有权限查询生产订单状态变更日志")
    public List<ProductionOrderStatusLog> selectOrderStausLog(@NotBlank String orderId) {
        LambdaQueryWrapper<ProductionOrderStatusLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ProductionOrderStatusLog::getTenantCode, TenantPermissionContext.getTenantCode());
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
    public ProductionOrder advanceByFlowCode(String flowCode) {
        String orderId = resolveOrderIdFromFlowCode(flowCode);
        ProductionOrder order = productionOrderMapper.selectOne(new LambdaQueryWrapper<ProductionOrder>()
                .eq(ProductionOrder::getTenantCode, TenantPermissionContext.getTenantCode())
                .eq(ProductionOrder::getOrderId, orderId)
                .last("LIMIT 1"));
        if (order == null) {
            throw new BusinessException(400, "订单不存在");
        }

        ProductionOrderUpdateRequest request = new ProductionOrderUpdateRequest();
        request.setOperateType(OrderOperateTypeEnum.SCAN_CHANGE.getCode());

        String currentStatus = StringUtils.isNotBlank(order.getStatus())
                ? order.getStatus().trim()
                : OrderStatusEnum.PENDING_CONFIRM.getCode();
        if (OrderStatusEnum.COMPLETED.getCode().equals(currentStatus)) {
            throw new BusinessException(400, "订单已完成，无需继续流转");
        }

        if (STATUS_PRODUCING.equals(currentStatus)) {
            Integer currentProcess = order.getProcess();
            Integer finalProcess = ProcessEnum.FINISHED_SHIPPING.getCode();
            if (currentProcess == null || currentProcess < finalProcess) {
                int nextProcess = currentProcess == null ? 0 : currentProcess + 1;
                ProcessEnum next = ProcessEnum.getByCode(nextProcess);
                request.setStatus(STATUS_PRODUCING);
                request.setProcess(nextProcess);
                request.setRemark("扫码完成工序：" + next.getName());
                return updateStatusAndProcess(orderId, request);
            }

            request.setStatus(STATUS_PENDING_SHIP);
            request.setRemark("扫码完成全部生产工序，订单进入" + statusLabel(STATUS_PENDING_SHIP));
            return updateStatusAndProcess(orderId, request);
        }

        String nextStatus = resolveNextProductionStatus(currentStatus);
        if (!StringUtils.isNotBlank(nextStatus)) {
            throw new BusinessException(400, "当前状态无法继续流转");
        }
        request.setStatus(nextStatus);
        if (STATUS_PRODUCING.equals(nextStatus)) {
            request.setProcess(0);
            request.setRemark("扫码进入生产，并完成工序：" + ProcessEnum.MATERIAL_INBOUND.getName());
        } else {
            request.setRemark("扫码推进订单至" + statusLabel(nextStatus));
        }
        return updateStatusAndProcess(orderId, request);
    }

    private String resolveOrderIdFromFlowCode(String rawFlowCode) {
        try {
            OrderFlowCodeUtil.Parsed parsed = OrderFlowCodeUtil.parse(rawFlowCode);
            if (!"production".equals(parsed.orderType())) {
                throw new BusinessException(400, "请扫描生产订单流转码");
            }
            if (!OrderFlowCodeUtil.matches(orderFlowCodeSecret, TenantPermissionContext.getTenantCode(), parsed)) {
                throw new BusinessException(400, "订单流转码无效，请重新打印后再扫码");
            }
            return parsed.orderId();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(400, "订单流转码无效，请重新打印后再扫码");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @RequirePermission(value = PermissionCodeEnum.CODE_PRODUCTION_ORDER_STATUS, message = "您没有权限更新生产订单状态")
    public ProductionOrder updateStatusAndProcess(String orderId, ProductionOrderUpdateRequest request) {
        ProductionOrder order = productionOrderMapper.selectOne(new LambdaQueryWrapper<ProductionOrder>()
                .eq(ProductionOrder::getTenantCode, TenantPermissionContext.getTenantCode())
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

            if (!Objects.equals(oldStatus, targetStatus)) {
                try {
                    OrderStatusEnum oldStatusEnum = OrderStatusEnum.getByCode(oldStatus);
                    OrderStatusEnum targetStatusEnum = OrderStatusEnum.getByCode(targetStatus);
                    if (oldStatusEnum == null || !oldStatusEnum.canFlowTo(targetStatusEnum)) {
                        throw new BusinessException(400, "订单状态只能向后流转，不能回退或重复提交");
                    }
                } catch (IllegalArgumentException ex) {
                    throw new BusinessException(400, "订单状态不合法");
                }
            }

            if (STATUS_PRODUCING.equals(targetStatus) && !STATUS_PRODUCING.equals(order.getStatus())) {
                Integer requestedProcess = request.getProcess();
                if (requestedProcess != null && requestedProcess > 0) {
                    throw new BusinessException(400, "生产工序必须从原料入库开始");
                }
                order.setProcess(requestedProcess);
                request.setProcess(null);
            } else if (STATUS_PENDING_SHIP.equals(targetStatus) && STATUS_PRODUCING.equals(order.getStatus())) {
                Integer requestedProcess = request.getProcess();
                Integer currentProcess = order.getProcess();
                Integer finalProcess = ProcessEnum.FINISHED_SHIPPING.getCode();
                if (requestedProcess != null && !Objects.equals(requestedProcess, currentProcess)) {
                    throw new BusinessException(400, "流转待发货时不能同时变更生产工序");
                }
                if (!Objects.equals(currentProcess, finalProcess)) {
                    throw new BusinessException(400, "请先完成成品发货工序");
                }
                request.setProcess(null);
            } else if (!STATUS_PRODUCING.equals(targetStatus)) {
                order.setProcess(null);
                request.setProcess(null);
            }
            order.setStatus(targetStatus);
        }

        if (request.getProcess() != null) {
            if (!STATUS_PRODUCING.equals(order.getStatus())) {
                throw new BusinessException(400, "只有生产中的订单才能更新生产工序");
            }
            try {
                ProcessEnum.getByCode(request.getProcess());
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(400, "生产工序不合法");
            }
            if (oldProcess != null && request.getProcess() < oldProcess) {
                throw new BusinessException(400, "生产工序不能回退");
            }
            if (oldProcess == null && request.getProcess() > 0) {
                throw new BusinessException(400, "生产工序必须从原料入库开始");
            }
            if (oldProcess != null && request.getProcess() > oldProcess + 1) {
                throw new BusinessException(400, "生产工序不能跳级流转");
            }
            order.setProcess(request.getProcess());
        }

        order.setUpdater(resolveCurrentUserIdText());

        LambdaUpdateWrapper<ProductionOrder> updateWrapper = new LambdaUpdateWrapper<ProductionOrder>()
                .eq(ProductionOrder::getTenantCode, TenantPermissionContext.getTenantCode())
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

        boolean changed = !Objects.equals(oldStatus, order.getStatus()) || !Objects.equals(oldProcess, order.getProcess());
        if (changed || StringUtils.isNotBlank(request.getRemark())) {
            insertStatusLog(orderId, oldStatus, oldProcess, order.getStatus(), order.getProcess(), request);
        }
        if (changed) {
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
        productionOrder.setOrderCategory(OrderCategoryEnum.normalize(productionOrder.getOrderCategory()));
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

    private void fillProductionProcessView(ProductionOrderVO vo, String status, Integer process) {
        ProcessEnum[] processEnums = ProcessEnum.values();
        int total = processEnums.length;
        int completedIndex = resolveCompletedProcessIndex(status, process, total);
        int currentIndex = resolveCurrentProcessIndex(status, completedIndex, total);
        List<ProductionProcessStepVO> steps = Arrays.stream(processEnums)
                .map(item -> {
                    ProductionProcessStepVO step = new ProductionProcessStepVO();
                    step.setCode(item.getCode());
                    step.setName(item.getName());
                    step.setDone(completedIndex >= item.getCode());
                    step.setCurrent(currentIndex == item.getCode());
                    return step;
                })
                .toList();
        String completedText = completedIndex >= 0 ? processEnums[completedIndex].getName() : "";
        String currentText = currentIndex >= 0 ? processEnums[currentIndex].getName() : "";
        if (STATUS_PRODUCING.equals(status) && currentIndex < 0) {
            currentText = "生产工序已完成";
        }
        if (!STATUS_PRODUCING.equals(status) && currentIndex < 0 && completedIndex >= total - 1) {
            currentText = "生产工序已完成";
        }
        vo.setCompletedProcessText(completedText);
        vo.setCurrentProcessText(currentText);
        vo.setProcessText(StringUtils.isNotBlank(currentText) ? currentText : completedText);
        vo.setProcessProgressPercent(total <= 0 ? 0 : Math.max(0, Math.min(100, Math.round(((completedIndex + 1) * 100f) / total))));
        vo.setProcessSteps(steps);
    }

    private int resolveCompletedProcessIndex(String status, Integer process, int total) {
        if (total <= 0) {
            return -1;
        }
        if (OrderStatusEnum.PENDING_SHIP.getCode().equals(status)
                || OrderStatusEnum.SHIPPED.getCode().equals(status)
                || OrderStatusEnum.COMPLETED.getCode().equals(status)) {
            return total - 1;
        }
        if (!STATUS_PRODUCING.equals(status) || process == null) {
            return -1;
        }
        return Math.max(-1, Math.min(total - 1, process));
    }

    private int resolveCurrentProcessIndex(String status, int completedIndex, int total) {
        if (!STATUS_PRODUCING.equals(status) || total <= 0) {
            return -1;
        }
        int nextIndex = completedIndex + 1;
        return nextIndex >= 0 && nextIndex < total ? nextIndex : -1;
    }

    private String resolveNextProductionStatus(String currentStatus) {
        int index = PRODUCTION_STATUS_CODES.indexOf(currentStatus);
        if (index < 0 || index >= PRODUCTION_STATUS_CODES.size() - 1) {
            return "";
        }
        return PRODUCTION_STATUS_CODES.get(index + 1);
    }

}

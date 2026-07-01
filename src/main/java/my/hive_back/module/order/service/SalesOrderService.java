package my.hive_back.module.order.service;

import my.hive_back.module.sys.model.enums.PermissionCodeEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import my.hive.common.annotation.RequirePermission;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive.common.order.OrderFlowCodeUtil;
import my.hive_back.common.utils.CodeGeneratorUtil;
import my.hive_back.module.approval.service.ApprovalAuditorCandidateService;
import my.hive_back.module.approval.service.ApprovalDefaultAuditorService;
import my.hive_back.module.order.IsInvoiceEnum;
import my.hive_back.module.order.OrderCategoryEnum;
import my.hive_back.module.order.OrderStatusEnum;
import my.hive_back.module.order.mapper.SalesOrderDetailMapper;
import my.hive_back.module.order.mapper.SalesOrderMapper;
import my.hive_back.module.order.mapper.SalesOrderStatusLogMapper;
import my.hive_back.module.order.model.dto.*;
import my.hive_back.module.order.model.entity.SalesOrder;
import my.hive_back.module.order.model.entity.SalesOrderDetail;
import my.hive_back.module.order.model.entity.SalesOrderStatusLog;
import my.hive_back.module.order.model.vo.SalesOrderStatusLogVO;
import my.hive_back.module.order.model.vo.SalesOrderVO;
import my.hive_back.module.user.mapper.UserMapper;
import my.hive_back.module.user.model.entity.User;
import my.hive_back.module.wechat.service.WechatSubscribeNotificationService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
/**
 * SalesOrderService 属于小程序后端订单模块，实现核心业务编排与规则逻辑。
 */
@Slf4j
@Service
public class SalesOrderService {

    private static final long DEFAULT_PAGE_NUM = 1L;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 200L;
    private static final List<String> SALES_STATUS_CODES = List.of(
            OrderStatusEnum.BUDGETING.getCode(),
            OrderStatusEnum.BUDGET_COMPLETED.getCode(),
            OrderStatusEnum.PENDING_CONFIRM.getCode(),
            OrderStatusEnum.PENDING_PAY.getCode(),
            OrderStatusEnum.PENDING_MATERIAL.getCode(),
            OrderStatusEnum.PRODUCING.getCode(),
            OrderStatusEnum.PENDING_SHIP.getCode(),
            OrderStatusEnum.SHIPPED.getCode(),
            OrderStatusEnum.COMPLETED.getCode(),
            OrderStatusEnum.PENDING_CANCEL.getCode(),
            OrderStatusEnum.CANCELLED.getCode()
    );
    private static final String CATEGORY_DRAWING_BUDGET = OrderCategoryEnum.DRAWING_BUDGET.getCode();
    private static final String CATEGORY_SPECIAL_ORDER = OrderCategoryEnum.SPECIAL_ORDER.getCode();
    private static final String APPROVAL_TYPE_ORDER = "ORDER";
    private static final String ORDER_TYPE_SALES = "sales";
    private static final String OPERATE_TYPE_ROLLBACK_PENDING = "rollback_pending";
    private static final String OPERATE_TYPE_ROLLBACK_APPROVED = "rollback_approved";
    private static final int MAX_PARALLEL_APPROVERS = 10;

    @Resource
    private SalesOrderMapper salesOrderMapper;

    @Resource
    private ProductionOrderService productionOrderService;

    @Resource
    private SalesOrderDetailMapper salesOrderDetailMapper;

    @Resource
    private SalesOrderStatusLogMapper salesOrderStatusLogMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private CodeGeneratorUtil codeGeneratorUtil;

    @Resource
    private WechatSubscribeNotificationService wechatSubscribeNotificationService;

    @Resource
    private ApprovalAuditorCandidateService approvalAuditorCandidateService;

    @Resource
    private ApprovalDefaultAuditorService approvalDefaultAuditorService;

    @Value("${ORDER_FLOW_CODE_SECRET:${AUTH_TOKEN_SECRET:hive-local-order-flow-secret}}")
    private String orderFlowCodeSecret;

    @RequirePermission(value = PermissionCodeEnum.CODE_SALES_ORDER_LIST, message = "您没有权限查询销售订单列表")
    public Page<SalesOrderVO> selectSalesOrder(SalesOrderListRequest request) {
        // 1. 分页参数默认值处理（防御性编程）
        long pageNum = safePageNum(request.getPageNum());
        long pageSize = safePageSize(request.getPageSize());
        Page<SalesOrder> page = new Page<>(pageNum, pageSize);

        // 2. 构建查询条件（核心：空值判断 + OR 模糊查询）
        LambdaQueryWrapper<SalesOrder> queryWrapper = new LambdaQueryWrapper<>();
        // 状态：非空才拼接
        if (StringUtils.isNotBlank(request.getStatus())) {
            queryWrapper.eq(SalesOrder::getStatus, request.getStatus());
        }
        if (StringUtils.isNotBlank(request.getOrderCategory())) {
            queryWrapper.eq(SalesOrder::getOrderCategory, OrderCategoryEnum.normalize(request.getOrderCategory()));
        }
        if (request.getIsInvoice() != null) {
            queryWrapper.eq(SalesOrder::getIsInvoice, request.getIsInvoice() == IsInvoiceEnum.YES.getCode()
                    ? IsInvoiceEnum.YES.getCode()
                    : IsInvoiceEnum.NO.getCode());
        }
        // 关键词：订单号/客户名 模糊查询（OR 关系）
        String keyWord = request.getKeyWord();
        if (StringUtils.isNotBlank(keyWord)) {
            queryWrapper.and(wrapper -> wrapper
                    .like(SalesOrder::getOrderId, keyWord)
                    .or()
                    .like(SalesOrder::getCustomerName, keyWord)
                    .or()
                    .like(SalesOrder::getBrandName, keyWord)
            );
        }
        // 排序
        queryWrapper.orderByDesc(SalesOrder::getCreateTime);

        // 3. 查询主表分页数据
        Page<SalesOrder> orderPage = salesOrderMapper.selectPage(page, queryWrapper);
        List<SalesOrder> orderList = orderPage.getRecords();
        if (CollectionUtils.isEmpty(orderList)) {
            // 无数据直接返回空分页
            return new Page<>(pageNum, pageSize, 0);
        }

        // 4. 批量查询关联明细（核心优化：避免 N+1 查询）
        List<String> orderIds = orderList.stream()
                .map(SalesOrder::getOrderId)
                .collect(Collectors.toList());
        List<SalesOrderDetail> detailList = salesOrderDetailMapper.selectList(
                new LambdaQueryWrapper<SalesOrderDetail>()
                        .in(SalesOrderDetail::getOrderId, orderIds)
        );

        // 5. 明细按订单ID分组
        Map<String, List<SalesOrderDetail>> detailMap = detailList.stream()
                .collect(Collectors.groupingBy(SalesOrderDetail::getOrderId));

        // 6. 组装 主表+明细 VO
        List<SalesOrderVO> voList = orderList.stream().map(order -> {
            SalesOrderVO vo = new SalesOrderVO();
            BeanUtils.copyProperties(order, vo);

            // 对应订单id的商品明细列表
            List<SalesOrderDetail> itemList = detailMap.getOrDefault(order.getOrderId(), Collections.emptyList());
            List<SalesOrderVO.OrderItemVO> itemVOList = itemList.stream().map(detail -> {
                SalesOrderVO.OrderItemVO itemVO = new SalesOrderVO.OrderItemVO();
                BeanUtils.copyProperties(detail, itemVO);
                return itemVO;
            }).collect(Collectors.toList());

            vo.setItems(itemVOList);
            return vo;
        }).collect(Collectors.toList());

        // 7. 封装分页结果返回
        Page<SalesOrderVO> resultPage = new Page<>(pageNum, pageSize, orderPage.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    @RequirePermission(value = PermissionCodeEnum.CODE_SALES_ORDER_LIST, message = "您没有权限查询销售订单统计")
    public Map<String, Long> countSalesOrderStatuses() {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("total", safeCount(salesOrderMapper.selectCount(new LambdaQueryWrapper<>())));
        for (String status : SALES_STATUS_CODES) {
            result.put(status, safeCount(salesOrderMapper.selectCount(new LambdaQueryWrapper<SalesOrder>()
                    .eq(SalesOrder::getStatus, status))));
        }
        result.put("category_drawing_budget", safeCount(salesOrderMapper.selectCount(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getOrderCategory, OrderCategoryEnum.DRAWING_BUDGET.getCode()))));
        result.put("category_sample_room", safeCount(salesOrderMapper.selectCount(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getOrderCategory, OrderCategoryEnum.SAMPLE_ROOM.getCode()))));
        result.put("category_bulk", safeCount(salesOrderMapper.selectCount(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getOrderCategory, OrderCategoryEnum.BULK.getCode()))));
        result.put("category_replenishment", safeCount(salesOrderMapper.selectCount(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getOrderCategory, OrderCategoryEnum.REPLENISHMENT.getCode()))));
        result.put("category_special_order", safeCount(salesOrderMapper.selectCount(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getOrderCategory, OrderCategoryEnum.SPECIAL_ORDER.getCode()))));
        result.put("invoice_paid", safeCount(salesOrderMapper.selectCount(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getIsInvoice, IsInvoiceEnum.YES.getCode()))));
        result.put("invoice_unpaid", safeCount(salesOrderMapper.selectCount(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getIsInvoice, IsInvoiceEnum.NO.getCode()))));
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

    /**
     * 根据订单ID查询订单详情 (返回 VO 对象)
     */
    @RequirePermission(value = PermissionCodeEnum.CODE_SALES_ORDER_DETAIL, message = "您没有权限查询销售订单详情")
    public SalesOrderVO getByIdandTenantId(String orderId) {
        // 1. 查询主表订单信息
        SalesOrder order = salesOrderMapper.selectOne(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getOrderId, orderId));
        if (order == null) {
            throw new BusinessException(400, "订单不存在"); // 根据你的异常类调整
        }

        // 2. 查询对应的明细列表
        List<SalesOrderDetail> detailList = salesOrderDetailMapper.selectList(
                new LambdaQueryWrapper<SalesOrderDetail>()
                        .eq(SalesOrderDetail::getOrderId, orderId)
        );

        // 3. 转换主表：Entity -> VO
        SalesOrderVO orderVO = new SalesOrderVO();
        // 自动拷贝同名且类型相同的属性 (如 orderId, status, customerName 等)
        BeanUtils.copyProperties(order, orderVO);

        // 4. 转换明细表：List<Entity> -> List<VO>
        if (detailList != null && !detailList.isEmpty()) {
            List<SalesOrderVO.OrderItemVO> items = detailList.stream().map(detail -> {
                SalesOrderVO.OrderItemVO itemVO = new SalesOrderVO.OrderItemVO();

                BeanUtils.copyProperties(detail, itemVO);

                return itemVO;
            }).collect(Collectors.toList());

            // 将转换好的明细列表放入主 VO 中
            orderVO.setItems(items);
        } else {
            // 避免前端拿到 null 报错，给一个空集合兜底
            orderVO.setItems(new ArrayList<>());
        }

        orderVO.setLogs(selectSalesOrderStatusLog(orderId).stream()
                .map(this::toSalesLogVO)
                .collect(Collectors.toList()));
        return orderVO;
    }

    @RequirePermission(value = PermissionCodeEnum.CODE_SALES_ORDER_DETAIL, message = "您没有权限查询销售订单状态变更日志")
    public List<SalesOrderStatusLog> selectSalesOrderStatusLog(@NotBlank String orderId) {
        return salesOrderStatusLogMapper.selectList(new LambdaQueryWrapper<SalesOrderStatusLog>()
                .eq(SalesOrderStatusLog::getOrderId, orderId)
                .orderByAsc(SalesOrderStatusLog::getCreateTime));
    }


    @Transactional(rollbackFor = Exception.class)
    @RequirePermission(value = PermissionCodeEnum.CODE_SALES_ORDER_STATUS, message = "您没有权限更新销售订单状态")
    public SalesOrder advanceByFlowCode(@NotBlank String flowCode) {
        String orderId = resolveOrderIdFromFlowCode(flowCode);
        SalesOrder order = salesOrderMapper.selectOne(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getOrderId, orderId)
                .last("LIMIT 1"));
        if (order == null) {
            throw new BusinessException(400, "销售订单不存在");
        }
        String currentStatus = StringUtils.isNotBlank(order.getStatus())
                ? order.getStatus().trim()
                : OrderStatusEnum.PENDING_CONFIRM.getCode();
        if (OrderStatusEnum.PENDING_PAY.getCode().equals(currentStatus)) {
            throw new BusinessException(400, "待收款订单转备料中需要先通过订单审批");
        }
        String nextStatus = resolveNextSalesStatus(currentStatus);
        if (StringUtils.isBlank(nextStatus)) {
            throw new BusinessException(400, "当前状态无法继续流转");
        }
        if (OrderStatusEnum.SHIPPED.getCode().equals(nextStatus)) {
            throw new BusinessException(400, "发货前需要先补充物流信息");
        }
        SalesOrderUpdateRequest request = new SalesOrderUpdateRequest();
        request.setStatus(nextStatus);
        return updateStatusAndProcessInternal(orderId, request, false, "扫码推进订单至" + statusLabel(nextStatus));
    }

    private String resolveOrderIdFromFlowCode(String rawFlowCode) {
        try {
            OrderFlowCodeUtil.Parsed parsed = OrderFlowCodeUtil.parse(rawFlowCode);
            if (!"sales".equals(parsed.orderType())) {
                throw new BusinessException(400, "请扫描销售订单流转码");
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
    @RequirePermission(value = PermissionCodeEnum.CODE_SALES_ORDER_STATUS, message = "您没有权限添加销售订单")
    public void addSalesOrder(@Valid SalesOrderAddRequest request) {
        SalesOrder order = new SalesOrder();

        Integer createProductionOrder = request.getCreateProductionOrder();

        BeanUtils.copyProperties(request, order);
        String orderId = codeGeneratorUtil.generateSalesOrderCode();
        order.setOrderId(orderId);
        order.setIsInvoice(IsInvoiceEnum.NO.getCode());
        order.setTenantCode(TenantPermissionContext.getTenantCode());
        order.setOrderCategory(OrderCategoryEnum.normalize(request.getOrderCategory()));
        order.setStatus(defaultSalesStatus(order.getOrderCategory()));
        order.setCreator(resolveCurrentUserIdText());
        order.setUpdater(resolveCurrentUserIdText());
        order.setGoodsDesc(buildGoodsDesc(request.getItems()));
        order.setTotalQuantity(sumSalesQuantity(request.getItems()));
        salesOrderMapper.insert(order);
        insertSalesStatusLog(order, null, order.getStatus(), "create", "创建销售订单");


        List<SalesOrderAddRequest.OrderItemDTO> normalizedItems = normalizeOrderItems(request.getItems());
        normalizedItems.forEach(item -> {
            SalesOrderDetail detail = new SalesOrderDetail();
            detail.setOrderId(order.getOrderId());
            detail.setTenantCode(TenantPermissionContext.getTenantCode());
            BeanUtils.copyProperties(item, detail);
            salesOrderDetailMapper.insert(detail);
        });

        // 图纸预算订单只走预算状态，不进入生产单和审批中心。
        if (createProductionOrder == 1 && canAutoCreateProductionOrder(order.getOrderCategory())) {
            normalizedItems.stream().filter(item -> StringUtils.isNotBlank(item.getModelCode())).forEach(item -> {
                ProductionOrderAddRequest productionOrderRequest = new ProductionOrderAddRequest();
                BeanUtils.copyProperties(request, productionOrderRequest);
                productionOrderRequest.setModelCode(item.getModelCode());
                productionOrderRequest.setSpec(item.getSpec());
                productionOrderRequest.setQuantity(item.getQuantity() == null ? null : item.getQuantity().intValue());
                productionOrderRequest.setWeight(null);
                productionOrderService.addProductionOrder(productionOrderRequest, order.getOrderId());
            });
        }


    }

    private String buildGoodsDesc(List<SalesOrderAddRequest.OrderItemDTO> items) {
        List<SalesOrderAddRequest.OrderItemDTO> normalizedItems = normalizeOrderItems(items);
        if (normalizedItems.isEmpty()) {
            return null;
        }
        return normalizedItems.stream()
                .map(item -> {
                    String category = safeText(item.getWeight(), "");
                    String spec = numberText(item.getSpec());
                    return safeText(item.getModelCode(), "未填写型号")
                            + " / " + (StringUtils.isNotBlank(category) ? category : "未填写类别")
                            + " / " + (StringUtils.isNotBlank(spec) ? spec + "规格" : "未填写规格")
                            + " × " + (item.getQuantity() == null ? "未填写数量" : item.getQuantity().stripTrailingZeros().toPlainString());
                })
                .collect(Collectors.joining("；"));
    }

    private Integer sumSalesQuantity(List<SalesOrderAddRequest.OrderItemDTO> items) {
        BigDecimal total = normalizeOrderItems(items).stream()
                .map(SalesOrderAddRequest.OrderItemDTO::getQuantity)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.intValue();
    }

    private List<SalesOrderAddRequest.OrderItemDTO> normalizeOrderItems(List<SalesOrderAddRequest.OrderItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream()
                .filter(Objects::nonNull)
                .filter(this::hasOrderItemContent)
                .toList();
    }

    private boolean hasOrderItemContent(SalesOrderAddRequest.OrderItemDTO item) {
        return StringUtils.isNotBlank(item.getModelCode())
                || item.getQuantity() != null
                || StringUtils.isNotBlank(item.getWeight())
                || item.getSpec() != null;
    }

    private String safeText(String value, String fallback) {
        return StringUtils.isNotBlank(value) ? value.trim() : fallback;
    }

    private String numberText(Number value) {
        if (value == null) {
            return "";
        }
        return BigDecimal.valueOf(value.doubleValue()).stripTrailingZeros().toPlainString();
    }

    @Transactional(rollbackFor = Exception.class)
    @RequirePermission(value = PermissionCodeEnum.CODE_SALES_ORDER_STATUS, message = "您没有权限更新销售订单状态")
    public SalesOrder updateStatusAndProcess(@NotBlank String orderId, @Valid SalesOrderUpdateRequest request) {
        return updateStatusAndProcessInternal(orderId, request, false, "小程序更新销售订单状态");
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrder approvePendingPayToMaterial(@NotBlank String orderId, String remark) {
        SalesOrderUpdateRequest request = new SalesOrderUpdateRequest();
        request.setStatus(OrderStatusEnum.PENDING_MATERIAL.getCode());
        return updateStatusAndProcessInternal(orderId, request, true,
                StringUtils.isNotBlank(remark) ? remark.trim() : "审批通过，待收款订单转备料中");
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrder approvePendingConfirmToPay(@NotBlank String orderId, String remark) {
        SalesOrderUpdateRequest request = new SalesOrderUpdateRequest();
        request.setStatus(OrderStatusEnum.PENDING_PAY.getCode());
        return updateStatusAndProcessInternal(orderId, request, true,
                StringUtils.isNotBlank(remark) ? remark.trim() : "审批通过，订单创建生效");
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrder approvePendingCancelToCancelled(@NotBlank String orderId, String remark) {
        SalesOrderUpdateRequest request = new SalesOrderUpdateRequest();
        request.setStatus(OrderStatusEnum.CANCELLED.getCode());
        return updateStatusAndProcessInternal(orderId, request, true,
                StringUtils.isNotBlank(remark) ? remark.trim() : "审批通过，订单已取消");
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrder rejectPendingCancel(@NotBlank String orderId, String remark) {
        SalesOrder order = salesOrderMapper.selectOne(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getOrderId, orderId)
                .last("LIMIT 1"));
        if (order == null) {
            throw new BusinessException(400, "销售订单不存在");
        }
        if (!OrderStatusEnum.PENDING_CANCEL.getCode().equals(order.getStatus())) {
            throw new BusinessException(400, "当前订单不是取消审核中，不能驳回取消申请");
        }
        String oldStatus = order.getStatus();
        String restoredStatus = resolveStatusBeforePendingCancel(orderId);
        LambdaUpdateWrapper<SalesOrder> updateWrapper = new LambdaUpdateWrapper<SalesOrder>()
                .eq(SalesOrder::getOrderId, orderId)
                .eq(SalesOrder::getStatus, oldStatus)
                .set(SalesOrder::getStatus, restoredStatus)
                .set(SalesOrder::getUpdateTime, LocalDateTime.now());
        if (salesOrderMapper.update(null, updateWrapper) == 0) {
            throw new BusinessException(409, "订单状态已被其他操作更新，请刷新后重试");
        }
        order.setStatus(restoredStatus);
        order.setUpdateTime(LocalDateTime.now());
        String finalRemark = StringUtils.isNotBlank(remark) ? remark.trim() : "取消订单审核未通过，订单恢复原状态";
        insertSalesStatusLog(order, oldStatus, restoredStatus, "status_change", finalRemark);
        notifySalesOrderChanged(order, oldStatus);
        return order;
    }

    @Transactional(rollbackFor = Exception.class)
    @RequirePermission(value = PermissionCodeEnum.CODE_SALES_ORDER_STATUS, message = "您没有权限提交销售订单回退审批")
    public SalesOrder submitRollbackApproval(@NotBlank String orderId, SalesOrderUpdateRequest request) {
        SalesOrder order = salesOrderMapper.selectOne(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getOrderId, orderId)
                .last("LIMIT 1"));
        if (order == null) {
            throw new BusinessException(400, "销售订单不存在");
        }
        String currentStatus = normalizeStatus(order.getStatus());
        String targetStatus = request != null && StringUtils.isNotBlank(request.getStatus())
                ? normalizeStatus(request.getStatus())
                : resolvePreviousSalesStatus(order);
        validateSalesRollbackTarget(order, currentStatus, targetStatus);

        String approvalCode = orderApprovalCode(ORDER_TYPE_SALES, order.getOrderId());
        if (!approvalAuditorCandidateService.findPendingAuditorIds(
                order.getTenantCode(), APPROVAL_TYPE_ORDER, approvalCode).isEmpty()) {
            throw new BusinessException(400, "该订单已有待处理审批，请审批完成后再操作");
        }

        List<Long> auditorIds = normalizeApprovalAuditorIds(request == null ? null : request.getAuditorIds());
        if (auditorIds.isEmpty()) {
            auditorIds = approvalDefaultAuditorService.resolveAuditorIds(
                    order.getTenantCode(),
                    APPROVAL_TYPE_ORDER,
                    TenantPermissionContext.getUserId(),
                    null,
                    null,
                    PermissionCodeEnum.CODE_SALES_ORDER_STATUS,
                    false);
        }
        List<Long> permittedIds = userMapper.selectActiveApproverIdsByPermission(
                order.getTenantCode(), PermissionCodeEnum.CODE_SALES_ORDER_STATUS);
        for (Long auditorId : auditorIds) {
            if (permittedIds == null || !permittedIds.contains(auditorId)) {
                throw new BusinessException(400, "所选审批人没有销售订单审批权限");
            }
        }

        String remark = request == null ? null : trimToNull(request.getRemark());
        insertSalesStatusLog(order, currentStatus, targetStatus, OPERATE_TYPE_ROLLBACK_PENDING,
                StringUtils.isNotBlank(remark)
                        ? remark
                        : "提交订单回退审批：" + statusLabel(currentStatus) + " → " + statusLabel(targetStatus));
        approvalAuditorCandidateService.replaceActiveCandidates(
                order.getTenantCode(), APPROVAL_TYPE_ORDER, approvalCode, auditorIds);
        return order;
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrder approveRollback(@NotBlank String orderId, String remark) {
        SalesOrder order = salesOrderMapper.selectOne(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getOrderId, orderId)
                .last("LIMIT 1"));
        if (order == null) {
            throw new BusinessException(400, "销售订单不存在");
        }
        SalesOrderStatusLog rollbackLog = findPendingSalesRollbackLog(order.getOrderId());
        if (rollbackLog == null) {
            throw new BusinessException(400, "未找到待审批的订单回退申请");
        }
        String oldStatus = normalizeStatus(order.getStatus());
        String sourceStatus = normalizeStatus(rollbackLog.getOldStatus());
        String targetStatus = normalizeStatus(rollbackLog.getNewStatus());
        if (!Objects.equals(oldStatus, sourceStatus)) {
            throw new BusinessException(409, "订单状态已变化，请重新提交回退审批");
        }
        validateSalesRollbackTarget(order, oldStatus, targetStatus);

        LambdaUpdateWrapper<SalesOrder> updateWrapper = new LambdaUpdateWrapper<SalesOrder>()
                .eq(SalesOrder::getOrderId, order.getOrderId())
                .eq(SalesOrder::getStatus, oldStatus)
                .set(SalesOrder::getStatus, targetStatus)
                .set(SalesOrder::getUpdater, resolveCurrentUserIdText())
                .set(SalesOrder::getUpdateTime, LocalDateTime.now());
        if (salesOrderMapper.update(null, updateWrapper) == 0) {
            throw new BusinessException(409, "订单状态已被其他操作更新，请刷新后重试");
        }
        order.setStatus(targetStatus);
        order.setUpdater(resolveCurrentUserIdText());
        order.setUpdateTime(LocalDateTime.now());
        insertSalesStatusLog(order, oldStatus, targetStatus, OPERATE_TYPE_ROLLBACK_APPROVED,
                StringUtils.isNotBlank(remark) ? remark.trim() : "订单回退审批通过");
        notifySalesOrderChanged(order, oldStatus);
        return order;
    }

    private SalesOrder updateStatusAndProcessInternal(String orderId,
                                                      SalesOrderUpdateRequest request,
                                                      boolean approvalBypass,
                                                      String logRemark) {
        // 1. 查询当前销售订单
        SalesOrder order = salesOrderMapper.selectOne(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getOrderId, orderId));

        if (order == null) {
            throw new BusinessException(400, "销售订单不存在");
        }

        // --- 并发控制快照 ---
        // 记录修改前的原始状态，用于最后的 CAS 并发安全校验
        String oldStatus = order.getStatus();
        String targetStatus = request.getStatus();
        if (!approvalBypass
                && OrderStatusEnum.CANCELLED.getCode().equals(targetStatus)
                && !OrderStatusEnum.PENDING_CANCEL.getCode().equals(oldStatus)) {
            targetStatus = OrderStatusEnum.PENDING_CANCEL.getCode();
        }
        if (StringUtils.isBlank(targetStatus)) {
            throw new BusinessException(400, "目标状态不能为空");
        }
        OrderStatusEnum oldStatusEnum;
        OrderStatusEnum targetStatusEnum;
        try {
            oldStatusEnum = OrderStatusEnum.getByCode(oldStatus);
            targetStatusEnum = OrderStatusEnum.getByCode(targetStatus);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(400, "订单状态不合法");
        }
        validateSalesStatusTransition(order.getOrderCategory(), oldStatusEnum, targetStatusEnum);
        if (!approvalBypass
                && CATEGORY_SPECIAL_ORDER.equals(OrderCategoryEnum.normalize(order.getOrderCategory()))
                && OrderStatusEnum.PENDING_CONFIRM.getCode().equals(oldStatus)
                && OrderStatusEnum.PENDING_PAY.getCode().equals(targetStatus)) {
            throw new BusinessException(400, "特殊订单需要先通过订单审核，审核通过后才能创建成功");
        }
        if (!approvalBypass
                && OrderStatusEnum.PENDING_PAY.getCode().equals(oldStatus)
                && OrderStatusEnum.PENDING_MATERIAL.getCode().equals(targetStatus)) {
            throw new BusinessException(400, "待收款订单转备料中需要先通过订单审批");
        }

        // 2. 核心业务逻辑：状态与物流信息校验
        // 如果目标状态是“已发货 (shipped)”，强制要求填写完整的物流信息
        if (OrderStatusEnum.SHIPPED.getCode().equals(targetStatus)) {
            SalesOrderUpdateRequest.ExpressInfo expressInfo = request.getExpressInfo();
            if (expressInfo == null
                    || StringUtils.isBlank(expressInfo.getExpressCompany())
                    || StringUtils.isBlank(expressInfo.getExpressNo())) {
                throw new BusinessException(400, "发货操作必须填写完整的物流公司和物流单号");
            }
            // 填入物流信息，保留发货追溯依据。
            order.setExpressCompany(expressInfo.getExpressCompany().trim());
            order.setExpressNo(expressInfo.getExpressNo().trim());
        }
        // 如果是其他状态（如 pending_ship 待发货）
        // 这里采取覆盖策略，如果传了物流信息就更新，没传就不动
        else if (request.getExpressInfo() != null) {
            order.setExpressCompany(request.getExpressInfo().getExpressCompany());
            order.setExpressNo(request.getExpressInfo().getExpressNo());
        }

        // 更新订单状态和开票状态
        order.setStatus(targetStatus);
        if (request.getIsInvoice() != null) {
            order.setIsInvoice(request.getIsInvoice());
        }

        // 记录更新人
        order.setUpdater(resolveCurrentUserIdText());

        // 3. 并发安全更新（CAS核心改造点）
        LambdaUpdateWrapper<SalesOrder> updateWrapper = new LambdaUpdateWrapper<SalesOrder>()
                .eq(SalesOrder::getOrderId, orderId);

        // 校验旧状态
        if (oldStatus == null) {
            updateWrapper.isNull(SalesOrder::getStatus);
        } else {
            updateWrapper.eq(SalesOrder::getStatus, oldStatus);
        }

        // 执行更新，返回受影响的行数
        int updatedRows = salesOrderMapper.update(order, updateWrapper);

        // 如果受影响行数为 0，说明在查询和更新的时间差内，状态被其他线程改动了
        if (updatedRows == 0) {
            throw new BusinessException(409, "订单状态已被其他人修改，操作失败，请刷新后重试");
        }

        if (!Objects.equals(oldStatus, order.getStatus())) {
            insertSalesStatusLog(order, oldStatus, order.getStatus(), "status_change", logRemark);
            notifySalesOrderChanged(order, oldStatus);
            if (OrderStatusEnum.PENDING_PAY.getCode().equals(oldStatus)
                    && OrderStatusEnum.PENDING_MATERIAL.getCode().equals(order.getStatus())) {
                productionOrderService.syncLinkedSalesOrderToPendingMaterial(order.getOrderId(), logRemark);
            }
        }

        return order;
    }

    private void insertSalesStatusLog(SalesOrder order, String oldStatus, String newStatus, String operateType, String remark) {
        SalesOrderStatusLog log = new SalesOrderStatusLog();
        log.setTenantCode(order.getTenantCode());
        log.setOrderId(order.getOrderId());
        log.setOldStatus(oldStatus);
        log.setNewStatus(newStatus);
        log.setOperateType(operateType);
        log.setRemark(remark);
        log.setOperator(String.valueOf(TenantPermissionContext.getUserId()));
        log.setOperatorName(resolveCurrentUserName());
        log.setCreateTime(LocalDateTime.now());
        salesOrderStatusLogMapper.insert(log);
    }

    public SalesOrderStatusLog findPendingSalesRollbackLog(String orderId) {
        if (StringUtils.isBlank(orderId)) {
            return null;
        }
        return salesOrderStatusLogMapper.selectOne(new LambdaQueryWrapper<SalesOrderStatusLog>()
                .eq(SalesOrderStatusLog::getOrderId, orderId.trim())
                .eq(SalesOrderStatusLog::getOperateType, OPERATE_TYPE_ROLLBACK_PENDING)
                .orderByDesc(SalesOrderStatusLog::getId)
                .last("LIMIT 1"));
    }

    public boolean hasPendingSalesRollbackApproval(String orderId) {
        SalesOrder order = StringUtils.isNotBlank(orderId) ? salesOrderMapper.selectOne(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getOrderId, orderId.trim())
                .last("LIMIT 1")) : null;
        if (order == null) {
            return false;
        }
        SalesOrderStatusLog log = findPendingSalesRollbackLog(order.getOrderId());
        if (log == null || !Objects.equals(normalizeStatus(order.getStatus()), normalizeStatus(log.getOldStatus()))) {
            return false;
        }
        return !approvalAuditorCandidateService.findPendingAuditorIds(
                order.getTenantCode(), APPROVAL_TYPE_ORDER, orderApprovalCode(ORDER_TYPE_SALES, order.getOrderId())).isEmpty();
    }

    private String orderApprovalCode(String orderType, String orderId) {
        if (StringUtils.isBlank(orderId)) {
            throw new BusinessException(400, "订单编号不能为空");
        }
        String type = ORDER_TYPE_SALES.equalsIgnoreCase(orderType) ? ORDER_TYPE_SALES : ORDER_TYPE_SALES;
        return type + ":" + orderId.trim();
    }

    private String normalizeStatus(String status) {
        return StringUtils.isBlank(status) ? null : status.trim();
    }

    private void validateSalesRollbackTarget(SalesOrder order, String currentStatus, String targetStatus) {
        if (StringUtils.isBlank(currentStatus) || StringUtils.isBlank(targetStatus)) {
            throw new BusinessException(400, "订单回退状态不能为空");
        }
        if (OrderStatusEnum.PENDING_CANCEL.getCode().equals(currentStatus)
                || OrderStatusEnum.CANCELLED.getCode().equals(currentStatus)
                || OrderStatusEnum.PENDING_CANCEL.getCode().equals(targetStatus)
                || OrderStatusEnum.CANCELLED.getCode().equals(targetStatus)) {
            throw new BusinessException(400, "取消审核中或已取消订单不能提交回退审批");
        }
        String expectedTarget = resolvePreviousSalesStatus(order);
        if (!Objects.equals(expectedTarget, targetStatus)) {
            throw new BusinessException(400, "订单只能回退到上一步状态：" + statusLabel(expectedTarget));
        }
    }

    private String resolvePreviousSalesStatus(SalesOrder order) {
        String currentStatus = normalizeStatus(order == null ? null : order.getStatus());
        if (StringUtils.isBlank(currentStatus)) {
            throw new BusinessException(400, "当前订单状态不能为空");
        }
        boolean drawingBudget = isDrawingBudgetOrder(order.getOrderCategory());
        if (drawingBudget) {
            if (OrderStatusEnum.BUDGET_COMPLETED.getCode().equals(currentStatus)) {
                return OrderStatusEnum.BUDGETING.getCode();
            }
            throw new BusinessException(400, "图纸预算订单当前状态不能回退");
        }
        if (isBudgetStatus(currentStatus)) {
            throw new BusinessException(400, "普通订单不能使用预算状态回退");
        }
        if (OrderStatusEnum.PENDING_PAY.getCode().equals(currentStatus)) {
            return OrderStatusEnum.PENDING_CONFIRM.getCode();
        }
        if (OrderStatusEnum.PENDING_MATERIAL.getCode().equals(currentStatus)) {
            return OrderStatusEnum.PENDING_PAY.getCode();
        }
        if (OrderStatusEnum.PRODUCING.getCode().equals(currentStatus)) {
            return OrderStatusEnum.PENDING_MATERIAL.getCode();
        }
        if (OrderStatusEnum.PENDING_SHIP.getCode().equals(currentStatus)) {
            return OrderStatusEnum.PRODUCING.getCode();
        }
        if (OrderStatusEnum.SHIPPED.getCode().equals(currentStatus)) {
            return OrderStatusEnum.PENDING_SHIP.getCode();
        }
        if (OrderStatusEnum.COMPLETED.getCode().equals(currentStatus)) {
            return OrderStatusEnum.SHIPPED.getCode();
        }
        throw new BusinessException(400, "当前订单状态不能回退");
    }

    private List<Long> normalizeApprovalAuditorIds(List<Long> auditorIds) {
        if (auditorIds == null || auditorIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        Long currentUserId = TenantPermissionContext.getUserId();
        for (Long auditorId : auditorIds) {
            if (auditorId == null || auditorId <= 0) {
                continue;
            }
            if (currentUserId != null && currentUserId.equals(auditorId)) {
                throw new BusinessException(400, "审批人不能选择提交人本人");
            }
            ids.add(auditorId);
        }
        if (ids.size() > MAX_PARALLEL_APPROVERS) {
            throw new BusinessException(400, "审批人不能超过 " + MAX_PARALLEL_APPROVERS + " 人");
        }
        return new ArrayList<>(ids);
    }

    private String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private String resolveStatusBeforePendingCancel(String orderId) {
        SalesOrderStatusLog latestCancelLog = salesOrderStatusLogMapper.selectOne(new LambdaQueryWrapper<SalesOrderStatusLog>()
                .eq(SalesOrderStatusLog::getOrderId, orderId)
                .eq(SalesOrderStatusLog::getNewStatus, OrderStatusEnum.PENDING_CANCEL.getCode())
                .orderByDesc(SalesOrderStatusLog::getId)
                .last("LIMIT 1"));
        if (latestCancelLog == null || StringUtils.isBlank(latestCancelLog.getOldStatus())) {
            throw new BusinessException(400, "缺少取消申请来源状态，无法驳回取消申请");
        }
        String restoredStatus = latestCancelLog.getOldStatus().trim();
        if (OrderStatusEnum.PENDING_CANCEL.getCode().equals(restoredStatus)
                || OrderStatusEnum.CANCELLED.getCode().equals(restoredStatus)) {
            throw new BusinessException(400, "取消申请来源状态异常，无法驳回取消申请");
        }
        return restoredStatus;
    }

    private SalesOrderStatusLogVO toSalesLogVO(SalesOrderStatusLog log) {
        SalesOrderStatusLogVO vo = new SalesOrderStatusLogVO();
        BeanUtils.copyProperties(log, vo);
        return vo;
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

    private void notifySalesOrderChanged(SalesOrder order, String oldStatus) {
        Long creatorId = parseUserId(order.getCreator());
        Long currentUserId = TenantPermissionContext.getUserId();
        if (creatorId == null || creatorId.equals(currentUserId)) {
            return;
        }
        wechatSubscribeNotificationService.sendTodoAfterCommit(
                creatorId,
                currentOperatorName(),
                "销售订单状态更新",
                order.getOrderId() + "：" + statusLabel(oldStatus) + " → " + statusLabel(order.getStatus()),
                "/pages/orderDetail/orderDetail?type=sales&orderId=" + order.getOrderId()
        );
    }

    private String currentOperatorName() {
        Long userId = TenantPermissionContext.getUserId();
        if (userId == null) {
            return "系统提醒";
        }
        User user = userMapper.selectById(userId);
        return user == null || user.getName() == null || user.getName().isBlank() ? "系统提醒" : user.getName();
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

    private String statusLabel(String status) {
        if (status == null || status.isBlank()) {
            return "未设置";
        }
        try {
            return OrderStatusEnum.getByCode(status).getName();
        } catch (IllegalArgumentException ex) {
            return status;
        }
    }

    private String defaultSalesStatus(String orderCategory) {
        return isDrawingBudgetOrder(orderCategory)
                ? OrderStatusEnum.BUDGETING.getCode()
                : OrderStatusEnum.PENDING_CONFIRM.getCode();
    }

    private void validateSalesStatusTransition(String orderCategory,
                                               OrderStatusEnum oldStatusEnum,
                                               OrderStatusEnum targetStatusEnum) {
        if (oldStatusEnum == null || targetStatusEnum == null) {
            throw new BusinessException(400, "订单状态不合法");
        }
        if (OrderStatusEnum.PENDING_CANCEL == targetStatusEnum) {
            if (OrderStatusEnum.CANCELLED == oldStatusEnum) {
                throw new BusinessException(400, "已取消的订单不能重复提交取消审核");
            }
            return;
        }
        if (OrderStatusEnum.CANCELLED == targetStatusEnum) {
            if (OrderStatusEnum.PENDING_CANCEL != oldStatusEnum) {
                throw new BusinessException(400, "取消订单需要先通过订单审核");
            }
            return;
        }
        boolean drawingBudget = isDrawingBudgetOrder(orderCategory);
        if (drawingBudget) {
            if (!isBudgetStatus(oldStatusEnum.getCode()) || !isBudgetStatus(targetStatusEnum.getCode())) {
                throw new BusinessException(400, "图纸预算订单只能在预算中和预算完成之间流转");
            }
            if (!OrderStatusEnum.BUDGETING.getCode().equals(oldStatusEnum.getCode())
                    || !OrderStatusEnum.BUDGET_COMPLETED.getCode().equals(targetStatusEnum.getCode())) {
                throw new BusinessException(400, "图纸预算订单只能从预算中流转到预算完成");
            }
            return;
        }
        if (isBudgetStatus(oldStatusEnum.getCode()) || isBudgetStatus(targetStatusEnum.getCode())) {
            throw new BusinessException(400, "普通订单不能使用预算状态");
        }
        if (targetStatusEnum.getIndex() <= oldStatusEnum.getIndex()) {
            throw new BusinessException(400, "订单状态只能向后流转，不能回退或重复提交");
        }
        if (targetStatusEnum.getIndex() > oldStatusEnum.getIndex() + 1) {
            throw new BusinessException(400, "订单状态只能推进到下一阶段，不能跳级流转");
        }
    }

    private boolean canAutoCreateProductionOrder(String orderCategory) {
        String normalized = OrderCategoryEnum.normalize(orderCategory);
        return !CATEGORY_DRAWING_BUDGET.equals(normalized) && !CATEGORY_SPECIAL_ORDER.equals(normalized);
    }

    private boolean isDrawingBudgetOrder(String orderCategory) {
        return CATEGORY_DRAWING_BUDGET.equals(OrderCategoryEnum.normalize(orderCategory));
    }

    private boolean isBudgetStatus(String status) {
        return OrderStatusEnum.BUDGETING.getCode().equals(status)
                || OrderStatusEnum.BUDGET_COMPLETED.getCode().equals(status);
    }

    private String resolveNextSalesStatus(String currentStatus) {
        if (OrderStatusEnum.COMPLETED.getCode().equals(currentStatus)
                || OrderStatusEnum.PENDING_CANCEL.getCode().equals(currentStatus)
                || OrderStatusEnum.CANCELLED.getCode().equals(currentStatus)) {
            return "";
        }
        int index = SALES_STATUS_CODES.indexOf(currentStatus);
        if (index < 0 || index >= SALES_STATUS_CODES.size() - 1) {
            return "";
        }
        return SALES_STATUS_CODES.get(index + 1);
    }
}

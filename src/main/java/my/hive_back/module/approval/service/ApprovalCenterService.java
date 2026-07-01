package my.hive_back.module.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive_back.module.approval.model.dto.OrderApprovalAuditRequest;
import my.hive_back.module.approval.model.vo.ApprovalAuditorOptionVO;
import my.hive_back.module.approval.model.vo.ApprovalSummaryVO;
import my.hive_back.module.approval.model.vo.OrderApprovalVO;
import my.hive_back.module.finance.FinanceApprovalStatusEnum;
import my.hive_back.module.finance.mapper.FinanceApprovalMapper;
import my.hive_back.module.finance.model.entity.FinanceApproval;
import my.hive_back.module.leave.ApprovalActionEnum;
import my.hive_back.module.leave.LeaveStatusEnum;
import my.hive_back.module.leave.mapper.LeaveMapper;
import my.hive_back.module.leave.model.entity.UserLeave;
import my.hive_back.module.order.OrderCategoryEnum;
import my.hive_back.module.order.OrderStatusEnum;
import my.hive_back.module.order.mapper.ProductionOrderMapper;
import my.hive_back.module.order.mapper.SalesOrderMapper;
import my.hive_back.module.order.model.dto.ProductionOrderUpdateRequest;
import my.hive_back.module.order.model.entity.ProductionOrder;
import my.hive_back.module.order.model.entity.ProductionOrderStatusLog;
import my.hive_back.module.order.model.entity.SalesOrder;
import my.hive_back.module.order.model.entity.SalesOrderStatusLog;
import my.hive_back.module.order.service.ProductionOrderService;
import my.hive_back.module.order.service.SalesOrderService;
import my.hive_back.module.resignation.mapper.ResignationApprovalMapper;
import my.hive_back.module.resignation.model.entity.ResignationApproval;
import my.hive_back.module.sys.model.enums.PermissionCodeEnum;
import my.hive_back.module.user.mapper.UserMapper;
import my.hive_back.module.user.model.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Aggregates approval center counters and order confirmation approvals.
 */
@Service
public class ApprovalCenterService {

    private static final String ORDER_TYPE_SALES = "sales";
    private static final String ORDER_TYPE_PRODUCTION = "production";
    private static final String APPROVAL_TYPE_ORDER = "ORDER";
    private static final int RESIGNATION_STATUS_PENDING = 1;
    private static final int DEFAULT_AUDITOR_OPTION_LIMIT = 20;
    private static final int MAX_AUDITOR_OPTION_LIMIT = 50;

    @Resource
    private LeaveMapper leaveMapper;

    @Resource
    private FinanceApprovalMapper financeApprovalMapper;

    @Resource
    private ResignationApprovalMapper resignationApprovalMapper;

    @Resource
    private SalesOrderMapper salesOrderMapper;

    @Resource
    private ProductionOrderMapper productionOrderMapper;

    @Resource
    private SalesOrderService salesOrderService;

    @Resource
    private ProductionOrderService productionOrderService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private ApprovalAuditorCandidateService approvalAuditorCandidateService;

    @Resource
    private ApprovalDefaultAuditorService approvalDefaultAuditorService;

    public ApprovalSummaryVO summary() {
        Long userId = TenantPermissionContext.getUserId();
        String tenantCode = TenantPermissionContext.getTenantCode();
        LambdaQueryWrapper<UserLeave> leaveWrapper = new LambdaQueryWrapper<UserLeave>()
                .eq(UserLeave::getStatus, LeaveStatusEnum.PENDING.getCode());
        appendLeaveAuditorFilter(leaveWrapper, userId);
        long leavePending = safeCount(leaveMapper.selectCount(leaveWrapper));

        LambdaQueryWrapper<FinanceApproval> financeWrapper = new LambdaQueryWrapper<FinanceApproval>()
                .eq(FinanceApproval::getStatus, FinanceApprovalStatusEnum.PENDING.getCode());
        appendFinanceAuditorFilter(financeWrapper, userId);
        long financePending = safeCount(financeApprovalMapper.selectCount(financeWrapper));

        LambdaQueryWrapper<ResignationApproval> resignationWrapper = new LambdaQueryWrapper<ResignationApproval>()
                .eq(ResignationApproval::getStatus, RESIGNATION_STATUS_PENDING);
        appendResignationAuditorFilter(resignationWrapper, userId);
        long resignationPending = safeCount(resignationApprovalMapper.selectCount(resignationWrapper));
        long orderPending = countPendingOrders(tenantCode, userId);

        ApprovalSummaryVO vo = new ApprovalSummaryVO();
        vo.setLeavePending(leavePending);
        vo.setFinancePending(financePending);
        vo.setResignationPending(resignationPending);
        vo.setOrderPending(orderPending);
        vo.setTotalPending(leavePending + financePending + resignationPending + orderPending);
        return vo;
    }

    public List<ApprovalAuditorOptionVO> listAuditorOptions(String type, String keyword, Integer limit) {
        String tenantCode = TenantPermissionContext.getTenantCode();
        String permissionCode = resolveAuditorPermissionCode(type);
        if (!StringUtils.hasText(tenantCode) || !StringUtils.hasText(permissionCode)) {
            return List.of();
        }
        String normalizedType = approvalDefaultAuditorService.normalizeType(type);
        return approvalDefaultAuditorService.applyDefaultMark(normalizedType, userMapper.selectActiveApproverOptionsByPermission(
                tenantCode,
                permissionCode,
                trimToNull(keyword),
                safeAuditorOptionLimit(limit)
        ));
    }

    public List<OrderApprovalVO> listOrderApprovals() {
        String tenantCode = TenantPermissionContext.getTenantCode();
        Long currentUserId = TenantPermissionContext.getUserId();
        List<OrderApprovalVO> salesRows = salesOrderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                        .in(SalesOrder::getStatus,
                                OrderStatusEnum.PENDING_CONFIRM.getCode(),
                                OrderStatusEnum.PENDING_PAY.getCode(),
                                OrderStatusEnum.PENDING_CANCEL.getCode())
                        .orderByDesc(SalesOrder::getCreateTime))
                .stream()
                .map(this::toSalesVO)
                .toList();
        List<OrderApprovalVO> productionRows = productionOrderMapper.selectList(new LambdaQueryWrapper<ProductionOrder>()
                        .eq(ProductionOrder::getStatus, OrderStatusEnum.PENDING_CONFIRM.getCode())
                        .orderByDesc(ProductionOrder::getCreateTime))
                .stream()
                .map(this::toProductionVO)
                .toList();
        List<OrderApprovalVO> rollbackRows = approvalAuditorCandidateService
                .findPendingApprovalCodes(tenantCode, APPROVAL_TYPE_ORDER, currentUserId)
                .stream()
                .filter(code -> code != null && code.startsWith(ORDER_TYPE_SALES + ":"))
                .map(code -> code.substring((ORDER_TYPE_SALES + ":").length()))
                .distinct()
                .map(this::findSalesOrderForApprovalOrNull)
                .filter(order -> order != null && salesOrderService.hasPendingSalesRollbackApproval(order.getOrderId()))
                .map(this::toSalesVO)
                .toList();
        List<OrderApprovalVO> productionRollbackRows = approvalAuditorCandidateService
                .findPendingApprovalCodes(tenantCode, APPROVAL_TYPE_ORDER, currentUserId)
                .stream()
                .filter(code -> code != null && code.startsWith(ORDER_TYPE_PRODUCTION + ":"))
                .map(code -> code.substring((ORDER_TYPE_PRODUCTION + ":").length()))
                .distinct()
                .map(this::findProductionOrderForApprovalOrNull)
                .filter(order -> order != null && productionOrderService.hasPendingProductionRollbackApproval(order.getOrderId()))
                .map(this::toProductionVO)
                .toList();
        LinkedHashSet<String> seenOrderKeys = new LinkedHashSet<>();
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.concat(java.util.stream.Stream.concat(salesRows.stream(), productionRows.stream()), rollbackRows.stream()),
                        productionRollbackRows.stream())
                .filter(row -> Boolean.TRUE.equals(row.getCanAudit()))
                .filter(row -> seenOrderKeys.add(row.getOrderType() + ":" + row.getOrderId()))
                .sorted(Comparator.comparing(OrderApprovalVO::getCreateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public OrderApprovalVO detail(String orderType, String orderId) {
        if (ORDER_TYPE_SALES.equalsIgnoreCase(orderType)) {
            return toSalesVO(findSalesOrderForApproval(orderId));
        }
        if (ORDER_TYPE_PRODUCTION.equalsIgnoreCase(orderType)) {
            return toProductionVO(findProductionOrderForApproval(orderId));
        }
        throw new BusinessException("订单审批类型不合法");
    }

    @Transactional(rollbackFor = Exception.class)
    public void audit(OrderApprovalAuditRequest request) {
        String remark = StringUtils.hasText(request.getComment()) ? request.getComment().trim() : "审批中心确认订单";
        if (ORDER_TYPE_SALES.equalsIgnoreCase(request.getOrderType())) {
            SalesOrder salesOrder = findSalesOrderForApproval(request.getOrderId());
            validateCurrentOrderAuditor(ORDER_TYPE_SALES, salesOrder.getOrderId(), salesOrder.getTenantCode());
            String approvalCode = orderApprovalCode(ORDER_TYPE_SALES, salesOrder.getOrderId());
            boolean approve = request.getAction() != null && request.getAction() == ApprovalActionEnum.APPROVE.getCode();
            boolean candidateFlow = markCandidateDecisionIfPresent(
                    salesOrder.getTenantCode(), APPROVAL_TYPE_ORDER, approvalCode, TenantPermissionContext.getUserId(), approve, remark);
            if (!approve) {
                if (salesOrderService.hasPendingSalesRollbackApproval(salesOrder.getOrderId())) {
                    approvalAuditorCandidateService.closeActiveCandidates(
                            salesOrder.getTenantCode(), APPROVAL_TYPE_ORDER, approvalCode);
                    return;
                }
                if (OrderStatusEnum.PENDING_CANCEL.getCode().equals(salesOrder.getStatus())) {
                    salesOrderService.rejectPendingCancel(request.getOrderId(), remark);
                    approvalAuditorCandidateService.closeActiveCandidates(
                            salesOrder.getTenantCode(), APPROVAL_TYPE_ORDER, approvalCode);
                    return;
                }
                throw new BusinessException("订单驳回/取消请到订单管理中处理，避免误改业务单据状态");
            }
            if (candidateFlow && approvalAuditorCandidateService.hasPendingAuditors(
                    salesOrder.getTenantCode(), APPROVAL_TYPE_ORDER, approvalCode)) {
                return;
            }
            if (salesOrderService.hasPendingSalesRollbackApproval(salesOrder.getOrderId())) {
                salesOrderService.approveRollback(request.getOrderId(), remark);
            } else if (OrderStatusEnum.PENDING_CANCEL.getCode().equals(salesOrder.getStatus())) {
                salesOrderService.approvePendingCancelToCancelled(request.getOrderId(), remark);
            } else if (OrderStatusEnum.PENDING_PAY.getCode().equals(salesOrder.getStatus())) {
                salesOrderService.approvePendingPayToMaterial(request.getOrderId(), remark);
            } else {
                salesOrderService.approvePendingConfirmToPay(request.getOrderId(), remark);
            }
            approvalAuditorCandidateService.closeActiveCandidates(
                    salesOrder.getTenantCode(), APPROVAL_TYPE_ORDER, approvalCode);
            return;
        }
        if (ORDER_TYPE_PRODUCTION.equalsIgnoreCase(request.getOrderType())) {
            ProductionOrder productionOrder = findProductionOrderForApproval(request.getOrderId());
            validateCurrentOrderAuditor(ORDER_TYPE_PRODUCTION, productionOrder.getOrderId(), productionOrder.getTenantCode());
            String approvalCode = orderApprovalCode(ORDER_TYPE_PRODUCTION, productionOrder.getOrderId());
            boolean approve = request.getAction() != null && request.getAction() == ApprovalActionEnum.APPROVE.getCode();
            boolean candidateFlow = markCandidateDecisionIfPresent(
                    productionOrder.getTenantCode(), APPROVAL_TYPE_ORDER, approvalCode, TenantPermissionContext.getUserId(), approve, remark);
            if (!approve) {
                if (productionOrderService.hasPendingProductionRollbackApproval(productionOrder.getOrderId())) {
                    approvalAuditorCandidateService.closeActiveCandidates(
                            productionOrder.getTenantCode(), APPROVAL_TYPE_ORDER, approvalCode);
                    return;
                }
                throw new BusinessException("订单驳回/取消请到订单管理中处理，避免误改业务单据状态");
            }
            if (candidateFlow && approvalAuditorCandidateService.hasPendingAuditors(
                    productionOrder.getTenantCode(), APPROVAL_TYPE_ORDER, approvalCode)) {
                return;
            }
            if (productionOrderService.hasPendingProductionRollbackApproval(productionOrder.getOrderId())) {
                productionOrderService.approveRollback(request.getOrderId(), remark);
            } else {
                ProductionOrderUpdateRequest updateRequest = new ProductionOrderUpdateRequest();
                updateRequest.setStatus(OrderStatusEnum.PENDING_MATERIAL.getCode());
                updateRequest.setRemark(remark);
                productionOrderService.updateStatusAndProcess(request.getOrderId(), updateRequest);
            }
            approvalAuditorCandidateService.closeActiveCandidates(
                    productionOrder.getTenantCode(), APPROVAL_TYPE_ORDER, approvalCode);
            return;
        }
        throw new BusinessException("订单审批类型不合法");
    }

    private OrderApprovalVO toSalesVO(SalesOrder order) {
        boolean cancelApproval = OrderStatusEnum.PENDING_CANCEL.getCode().equals(order.getStatus());
        boolean rollbackApproval = salesOrderService.hasPendingSalesRollbackApproval(order.getOrderId());
        SalesOrderStatusLog rollbackLog = rollbackApproval ? salesOrderService.findPendingSalesRollbackLog(order.getOrderId()) : null;
        boolean payToProductionApproval = OrderStatusEnum.PENDING_PAY.getCode().equals(order.getStatus());
        boolean specialCreateApproval = OrderStatusEnum.PENDING_CONFIRM.getCode().equals(order.getStatus())
                && OrderCategoryEnum.SPECIAL_ORDER.getCode().equals(OrderCategoryEnum.normalize(order.getOrderCategory()));
        OrderApprovalVO vo = new OrderApprovalVO();
        vo.setOrderType(ORDER_TYPE_SALES);
        vo.setOrderTypeText("销售订单");
        vo.setOrderId(order.getOrderId());
        vo.setCustomerName(order.getCustomerName());
        vo.setProjectName(order.getProjectName());
        String fallbackSummary = specialCreateApproval
                ? "特殊订单创建审核"
                : (payToProductionApproval ? "待审批转备料中销售订单" : "待确认销售订单");
        if (cancelApproval) {
            fallbackSummary = "取消订单审核";
        }
        if (rollbackApproval && rollbackLog != null) {
            fallbackSummary = "订单回退审核：" + statusLabel(rollbackLog.getOldStatus()) + " → " + statusLabel(rollbackLog.getNewStatus());
        }
        vo.setSummary(StringUtils.hasText(order.getGoodsDesc()) ? order.getGoodsDesc() : fallbackSummary);
        vo.setStatus(order.getStatus());
        vo.setStatusText(specialCreateApproval ? "待审核创建" : (payToProductionApproval ? "待审批转备料中" : "待确认"));
        if (cancelApproval) {
            vo.setStatusText("待审核取消");
        }
        if (rollbackApproval && rollbackLog != null) {
            vo.setStatusText("待审核回退至" + statusLabel(rollbackLog.getNewStatus()));
        }
        vo.setCreateTime(order.getCreateTime());
        applyOrderAuditor(vo, order.getTenantCode(), ORDER_TYPE_SALES, order.getOrderId());
        return vo;
    }

    private OrderApprovalVO toProductionVO(ProductionOrder order) {
        boolean rollbackApproval = productionOrderService.hasPendingProductionRollbackApproval(order.getOrderId());
        ProductionOrderStatusLog rollbackLog = rollbackApproval ? productionOrderService.findPendingProductionRollbackLog(order.getOrderId()) : null;
        OrderApprovalVO vo = new OrderApprovalVO();
        vo.setOrderType(ORDER_TYPE_PRODUCTION);
        vo.setOrderTypeText("生产订单");
        vo.setOrderId(order.getOrderId());
        vo.setCustomerName(order.getCustomerName());
        vo.setProjectName(order.getProjectName());
        String fallbackSummary = (StringUtils.hasText(order.getModelCode()) ? order.getModelCode() : "待确认生产订单")
                + " / 数量 " + (order.getQuantity() == null ? 0 : order.getQuantity());
        if (rollbackApproval && rollbackLog != null) {
            fallbackSummary = "生产订单回退审核：" + statusLabel(rollbackLog.getOldStatus()) + " → " + statusLabel(rollbackLog.getNewStatus());
        }
        vo.setSummary(fallbackSummary);
        vo.setStatus(order.getStatus());
        vo.setStatusText(rollbackApproval && rollbackLog != null ? "待审核回退至" + statusLabel(rollbackLog.getNewStatus()) : "待确认");
        vo.setCreateTime(order.getCreateTime());
        applyOrderAuditor(vo, order.getTenantCode(), ORDER_TYPE_PRODUCTION, order.getOrderId());
        return vo;
    }

    private SalesOrder findPendingSalesOrder(String orderId) {
        SalesOrder order = salesOrderMapper.selectOne(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getOrderId, orderId)
                .in(SalesOrder::getStatus,
                        OrderStatusEnum.PENDING_CONFIRM.getCode(),
                        OrderStatusEnum.PENDING_PAY.getCode(),
                        OrderStatusEnum.PENDING_CANCEL.getCode()));
        if (order == null) {
            throw new BusinessException("待审批销售订单不存在或已处理");
        }
        return order;
    }

    private SalesOrder findSalesOrderForApprovalOrNull(String orderId) {
        try {
            return findSalesOrderForApproval(orderId);
        } catch (BusinessException ex) {
            return null;
        }
    }

    private SalesOrder findSalesOrderForApproval(String orderId) {
        SalesOrder order = salesOrderMapper.selectOne(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getOrderId, orderId)
                .last("LIMIT 1"));
        if (order == null) {
            throw new BusinessException("待审批销售订单不存在或已处理");
        }
        if (OrderStatusEnum.PENDING_CONFIRM.getCode().equals(order.getStatus())
                || OrderStatusEnum.PENDING_PAY.getCode().equals(order.getStatus())
                || OrderStatusEnum.PENDING_CANCEL.getCode().equals(order.getStatus())
                || salesOrderService.hasPendingSalesRollbackApproval(order.getOrderId())) {
            return order;
        }
        throw new BusinessException("待审批销售订单不存在或已处理");
    }

    private ProductionOrder findPendingProductionOrder(String orderId) {
        ProductionOrder order = productionOrderMapper.selectOne(new LambdaQueryWrapper<ProductionOrder>()
                .eq(ProductionOrder::getOrderId, orderId)
                .eq(ProductionOrder::getStatus, OrderStatusEnum.PENDING_CONFIRM.getCode()));
        if (order == null) {
            throw new BusinessException("待确认生产订单不存在或已处理");
        }
        return order;
    }

    private ProductionOrder findProductionOrderForApprovalOrNull(String orderId) {
        try {
            return findProductionOrderForApproval(orderId);
        } catch (BusinessException ex) {
            return null;
        }
    }

    private ProductionOrder findProductionOrderForApproval(String orderId) {
        ProductionOrder order = productionOrderMapper.selectOne(new LambdaQueryWrapper<ProductionOrder>()
                .eq(ProductionOrder::getOrderId, orderId)
                .last("LIMIT 1"));
        if (order == null) {
            throw new BusinessException("待审批生产订单不存在或已处理");
        }
        if (OrderStatusEnum.PENDING_CONFIRM.getCode().equals(order.getStatus())
                || productionOrderService.hasPendingProductionRollbackApproval(order.getOrderId())) {
            return order;
        }
        throw new BusinessException("待审批生产订单不存在或已处理");
    }

    private long countPendingOrders(String tenantCode, Long currentUserId) {
        if (currentUserId == null) {
            return 0L;
        }
        long count = 0L;
        LinkedHashSet<String> seenOrderKeys = new LinkedHashSet<>();
        List<SalesOrder> salesOrders = salesOrderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                .in(SalesOrder::getStatus,
                        OrderStatusEnum.PENDING_CONFIRM.getCode(),
                        OrderStatusEnum.PENDING_PAY.getCode(),
                        OrderStatusEnum.PENDING_CANCEL.getCode()));
        for (SalesOrder order : salesOrders) {
            List<Long> auditorIds = ensureOrderAuditorIds(order.getTenantCode(), ORDER_TYPE_SALES, order.getOrderId());
            if (auditorIds.contains(currentUserId)
                    && approvalAuditorCandidateService.isPendingAuditor(
                    order.getTenantCode(), APPROVAL_TYPE_ORDER, orderApprovalCode(ORDER_TYPE_SALES, order.getOrderId()), currentUserId)) {
                seenOrderKeys.add(ORDER_TYPE_SALES + ":" + order.getOrderId());
                count++;
            }
        }
        List<ProductionOrder> productionOrders = productionOrderMapper.selectList(new LambdaQueryWrapper<ProductionOrder>()
                .eq(ProductionOrder::getStatus, OrderStatusEnum.PENDING_CONFIRM.getCode()));
        for (ProductionOrder order : productionOrders) {
            List<Long> auditorIds = ensureOrderAuditorIds(order.getTenantCode(), ORDER_TYPE_PRODUCTION, order.getOrderId());
            if (auditorIds.contains(currentUserId)
                    && approvalAuditorCandidateService.isPendingAuditor(
                    order.getTenantCode(), APPROVAL_TYPE_ORDER, orderApprovalCode(ORDER_TYPE_PRODUCTION, order.getOrderId()), currentUserId)) {
                seenOrderKeys.add(ORDER_TYPE_PRODUCTION + ":" + order.getOrderId());
                count++;
            }
        }
        for (String approvalCode : approvalAuditorCandidateService.findPendingApprovalCodes(tenantCode, APPROVAL_TYPE_ORDER, currentUserId)) {
            if (approvalCode == null || !approvalCode.startsWith(ORDER_TYPE_SALES + ":")) {
                continue;
            }
            String orderId = approvalCode.substring((ORDER_TYPE_SALES + ":").length());
            String key = ORDER_TYPE_SALES + ":" + orderId;
            if (seenOrderKeys.contains(key) || !salesOrderService.hasPendingSalesRollbackApproval(orderId)) {
                continue;
            }
            seenOrderKeys.add(key);
            count++;
        }
        for (String approvalCode : approvalAuditorCandidateService.findPendingApprovalCodes(tenantCode, APPROVAL_TYPE_ORDER, currentUserId)) {
            if (approvalCode == null || !approvalCode.startsWith(ORDER_TYPE_PRODUCTION + ":")) {
                continue;
            }
            String orderId = approvalCode.substring((ORDER_TYPE_PRODUCTION + ":").length());
            String key = ORDER_TYPE_PRODUCTION + ":" + orderId;
            if (seenOrderKeys.contains(key) || !productionOrderService.hasPendingProductionRollbackApproval(orderId)) {
                continue;
            }
            seenOrderKeys.add(key);
            count++;
        }
        return count;
    }

    private void applyOrderAuditor(OrderApprovalVO vo, String tenantCode, String orderType, String orderId) {
        List<Long> auditorIds = ensureOrderAuditorIds(tenantCode, orderType, orderId);
        Long auditorId = auditorIds.isEmpty() ? null : auditorIds.get(0);
        vo.setAuditorId(auditorId);
        vo.setAuditorIds(joinOrderAuditorIds(auditorIds));
        vo.setAuditorName(resolveOrderAuditorNames(auditorIds));
        vo.setCanAudit(approvalAuditorCandidateService.isPendingAuditor(
                tenantCode, APPROVAL_TYPE_ORDER, orderApprovalCode(orderType, orderId), TenantPermissionContext.getUserId()));
    }

    private List<Long> ensureOrderAuditorIds(String tenantCode, String orderType, String orderId) {
        String approvalCode = orderApprovalCode(orderType, orderId);
        List<Long> activeAuditors = approvalAuditorCandidateService.findPendingAuditorIds(
                tenantCode, APPROVAL_TYPE_ORDER, approvalCode);
        if (activeAuditors != null && !activeAuditors.isEmpty()) {
            return activeAuditors;
        }
        List<Long> auditorIds = approvalDefaultAuditorService.resolveAuditorIds(
                tenantCode,
                APPROVAL_TYPE_ORDER,
                null,
                null,
                null,
                resolveOrderAuditPermissionCode(orderType),
                false
        );
        approvalAuditorCandidateService.replaceActiveCandidates(
                tenantCode, APPROVAL_TYPE_ORDER, approvalCode, auditorIds);
        return auditorIds;
    }

    private void validateCurrentOrderAuditor(String orderType, String orderId, String tenantCode) {
        List<Long> auditorIds = ensureOrderAuditorIds(tenantCode, orderType, orderId);
        Long currentUserId = TenantPermissionContext.getUserId();
        if (!auditorIds.contains(currentUserId)
                || !approvalAuditorCandidateService.isPendingAuditor(
                tenantCode, APPROVAL_TYPE_ORDER, orderApprovalCode(orderType, orderId), currentUserId)) {
            throw new BusinessException("您不是该订单的当前审批人");
        }
    }

    private boolean canCurrentUserAudit(Long currentUserId, Long auditorId, String auditorIds) {
        return currentUserId != null && currentUserId.equals(auditorId);
    }

    private String orderApprovalCode(String orderType, String orderId) {
        if (!StringUtils.hasText(orderId)) {
            throw new BusinessException("订单编号不能为空");
        }
        String type = ORDER_TYPE_PRODUCTION.equalsIgnoreCase(orderType) ? ORDER_TYPE_PRODUCTION : ORDER_TYPE_SALES;
        return type + ":" + orderId.trim();
    }

    private String resolveOrderAuditPermissionCode(String orderType) {
        if (ORDER_TYPE_PRODUCTION.equalsIgnoreCase(orderType)) {
            return PermissionCodeEnum.CODE_PRODUCTION_ORDER_STATUS;
        }
        return PermissionCodeEnum.CODE_SALES_ORDER_STATUS;
    }

    private boolean markCandidateDecisionIfPresent(String tenantCode,
                                                   String approvalType,
                                                   String approvalCode,
                                                   Long auditorId,
                                                   boolean approve,
                                                   String comment) {
        if (!approvalAuditorCandidateService.isPendingAuditor(tenantCode, approvalType, approvalCode, auditorId)) {
            return false;
        }
        approvalAuditorCandidateService.markAuditorDecision(
                tenantCode,
                approvalType,
                approvalCode,
                auditorId,
                approve,
                comment
        );
        return true;
    }

    private String joinOrderAuditorIds(List<Long> ids) {
        return ids == null || ids.isEmpty()
                ? null
                : ids.stream().map(String::valueOf).reduce((left, right) -> left + "," + right).orElse(null);
    }

    private String resolveOrderAuditorNames(List<Long> auditorIds) {
        if (auditorIds == null || auditorIds.isEmpty()) {
            return "待分配";
        }
        List<String> names = new ArrayList<>();
        for (Long auditorId : auditorIds) {
            User auditor = userMapper.selectById(auditorId);
            if (auditor != null && StringUtils.hasText(auditor.getName())) {
                names.add(auditor.getName());
            }
        }
        return names.isEmpty() ? "待分配" : String.join("、", names);
    }

    private String resolveOrderAuditorName(Long auditorId) {
        if (auditorId == null || auditorId <= 0) {
            return "待分配";
        }
        User auditor = userMapper.selectById(auditorId);
        return auditor == null || !StringUtils.hasText(auditor.getName()) ? "待分配" : auditor.getName();
    }

    private String statusLabel(String status) {
        if (!StringUtils.hasText(status)) {
            return "未设置";
        }
        try {
            return OrderStatusEnum.getByCode(status).getName();
        } catch (IllegalArgumentException ex) {
            return status;
        }
    }

    private void appendLeaveAuditorFilter(LambdaQueryWrapper<UserLeave> wrapper, Long userId) {
        if (userId == null) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.and(q -> q.eq(UserLeave::getAuditorId, userId)
                .or()
                .apply("FIND_IN_SET({0}, auditor_ids) > 0", String.valueOf(userId)));
    }

    private void appendFinanceAuditorFilter(LambdaQueryWrapper<FinanceApproval> wrapper, Long userId) {
        if (userId == null) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.and(q -> q.eq(FinanceApproval::getAuditorId, userId)
                .or()
                .apply("FIND_IN_SET({0}, auditor_ids) > 0", String.valueOf(userId)));
    }

    private void appendResignationAuditorFilter(LambdaQueryWrapper<ResignationApproval> wrapper, Long userId) {
        if (userId == null) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.and(q -> q.eq(ResignationApproval::getAuditorId, userId)
                .or()
                .apply("FIND_IN_SET({0}, auditor_ids) > 0", String.valueOf(userId)));
    }

    private long safeCount(Long value) {
        return value == null ? 0L : value;
    }

    private int safeAuditorOptionLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_AUDITOR_OPTION_LIMIT;
        }
        return Math.min(limit, MAX_AUDITOR_OPTION_LIMIT);
    }

    private String resolveAuditorPermissionCode(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase();
        return switch (normalized) {
            case "leave" -> PermissionCodeEnum.CODE_APPROVAL_LEAVE_AUDIT;
            case "finance" -> PermissionCodeEnum.CODE_APPROVAL_FINANCE_AUDIT;
            case "resignation" -> PermissionCodeEnum.CODE_APPROVAL_RESIGNATION_AUDIT;
            case "order" -> PermissionCodeEnum.CODE_SALES_ORDER_STATUS;
            default -> throw new BusinessException("审批类型不合法");
        };
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}

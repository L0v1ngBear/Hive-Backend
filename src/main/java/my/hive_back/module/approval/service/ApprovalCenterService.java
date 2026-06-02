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
import my.hive_back.module.order.OrderStatusEnum;
import my.hive_back.module.order.mapper.ProductionOrderMapper;
import my.hive_back.module.order.mapper.SalesOrderMapper;
import my.hive_back.module.order.model.dto.ProductionOrderUpdateRequest;
import my.hive_back.module.order.model.dto.SalesOrderUpdateRequest;
import my.hive_back.module.order.model.entity.ProductionOrder;
import my.hive_back.module.order.model.entity.SalesOrder;
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

import java.util.Comparator;
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
                .eq(UserLeave::getTenantCode, tenantCode)
                .eq(UserLeave::getStatus, LeaveStatusEnum.PENDING.getCode());
        appendLeaveAuditorFilter(leaveWrapper, userId);
        long leavePending = safeCount(leaveMapper.selectCount(leaveWrapper));

        LambdaQueryWrapper<FinanceApproval> financeWrapper = new LambdaQueryWrapper<FinanceApproval>()
                .eq(FinanceApproval::getTenantCode, tenantCode)
                .eq(FinanceApproval::getStatus, FinanceApprovalStatusEnum.PENDING.getCode());
        appendFinanceAuditorFilter(financeWrapper, userId);
        long financePending = safeCount(financeApprovalMapper.selectCount(financeWrapper));

        LambdaQueryWrapper<ResignationApproval> resignationWrapper = new LambdaQueryWrapper<ResignationApproval>()
                .eq(ResignationApproval::getTenantCode, tenantCode)
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
                        .eq(SalesOrder::getTenantCode, tenantCode)
                        .in(SalesOrder::getStatus,
                                OrderStatusEnum.PENDING_CONFIRM.getCode(),
                                OrderStatusEnum.PENDING_PAY.getCode())
                        .orderByDesc(SalesOrder::getCreateTime))
                .stream()
                .map(this::toSalesVO)
                .toList();
        List<OrderApprovalVO> productionRows = productionOrderMapper.selectList(new LambdaQueryWrapper<ProductionOrder>()
                        .eq(ProductionOrder::getTenantCode, tenantCode)
                        .eq(ProductionOrder::getStatus, OrderStatusEnum.PENDING_CONFIRM.getCode())
                        .orderByDesc(ProductionOrder::getCreateTime))
                .stream()
                .map(this::toProductionVO)
                .toList();
        return java.util.stream.Stream.concat(salesRows.stream(), productionRows.stream())
                .filter(row -> canCurrentUserAudit(currentUserId, row.getAuditorId(), null))
                .sorted(Comparator.comparing(OrderApprovalVO::getCreateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public OrderApprovalVO detail(String orderType, String orderId) {
        if (ORDER_TYPE_SALES.equalsIgnoreCase(orderType)) {
            return toSalesVO(findPendingSalesOrder(orderId));
        }
        if (ORDER_TYPE_PRODUCTION.equalsIgnoreCase(orderType)) {
            return toProductionVO(findPendingProductionOrder(orderId));
        }
        throw new BusinessException("订单审批类型不合法");
    }

    @Transactional(rollbackFor = Exception.class)
    public void audit(OrderApprovalAuditRequest request) {
        if (request.getAction() == null || request.getAction() != ApprovalActionEnum.APPROVE.getCode()) {
            throw new BusinessException("订单驳回/取消请到订单管理中处理，避免误改业务单据状态");
        }
        String remark = StringUtils.hasText(request.getComment()) ? request.getComment().trim() : "审批中心确认订单";
        if (ORDER_TYPE_SALES.equalsIgnoreCase(request.getOrderType())) {
            SalesOrder salesOrder = findPendingSalesOrder(request.getOrderId());
            validateCurrentOrderAuditor(ORDER_TYPE_SALES, salesOrder.getOrderId(), salesOrder.getTenantCode());
            if (OrderStatusEnum.PENDING_PAY.getCode().equals(salesOrder.getStatus())) {
                salesOrderService.approvePendingPayToProducing(request.getOrderId(), remark);
            } else {
                SalesOrderUpdateRequest updateRequest = new SalesOrderUpdateRequest();
                updateRequest.setStatus(OrderStatusEnum.PENDING_PAY.getCode());
                salesOrderService.updateStatusAndProcess(request.getOrderId(), updateRequest);
            }
            approvalAuditorCandidateService.closeActiveCandidates(
                    salesOrder.getTenantCode(), APPROVAL_TYPE_ORDER, orderApprovalCode(ORDER_TYPE_SALES, salesOrder.getOrderId()));
            return;
        }
        if (ORDER_TYPE_PRODUCTION.equalsIgnoreCase(request.getOrderType())) {
            ProductionOrder productionOrder = findPendingProductionOrder(request.getOrderId());
            validateCurrentOrderAuditor(ORDER_TYPE_PRODUCTION, productionOrder.getOrderId(), productionOrder.getTenantCode());
            ProductionOrderUpdateRequest updateRequest = new ProductionOrderUpdateRequest();
            updateRequest.setStatus(OrderStatusEnum.PENDING_MATERIAL.getCode());
            updateRequest.setRemark(remark);
            productionOrderService.updateStatusAndProcess(request.getOrderId(), updateRequest);
            approvalAuditorCandidateService.closeActiveCandidates(
                    productionOrder.getTenantCode(), APPROVAL_TYPE_ORDER, orderApprovalCode(ORDER_TYPE_PRODUCTION, productionOrder.getOrderId()));
            return;
        }
        throw new BusinessException("订单审批类型不合法");
    }

    private OrderApprovalVO toSalesVO(SalesOrder order) {
        boolean payToProductionApproval = OrderStatusEnum.PENDING_PAY.getCode().equals(order.getStatus());
        OrderApprovalVO vo = new OrderApprovalVO();
        vo.setOrderType(ORDER_TYPE_SALES);
        vo.setOrderTypeText("销售订单");
        vo.setOrderId(order.getOrderId());
        vo.setCustomerName(order.getCustomerName());
        vo.setProjectName(order.getProjectName());
        String fallbackSummary = payToProductionApproval ? "待审批转生产中销售订单" : "待确认销售订单";
        vo.setSummary(StringUtils.hasText(order.getGoodsDesc()) ? order.getGoodsDesc() : fallbackSummary);
        vo.setStatus(order.getStatus());
        vo.setStatusText(payToProductionApproval ? "待审批转生产中" : "待确认");
        vo.setCreateTime(order.getCreateTime());
        applyOrderAuditor(vo, order.getTenantCode(), ORDER_TYPE_SALES, order.getOrderId());
        return vo;
    }

    private OrderApprovalVO toProductionVO(ProductionOrder order) {
        OrderApprovalVO vo = new OrderApprovalVO();
        vo.setOrderType(ORDER_TYPE_PRODUCTION);
        vo.setOrderTypeText("生产订单");
        vo.setOrderId(order.getOrderId());
        vo.setCustomerName(order.getCustomerName());
        vo.setProjectName(order.getProjectName());
        vo.setSummary((StringUtils.hasText(order.getModelCode()) ? order.getModelCode() : "待确认生产订单")
                + " / 数量 " + (order.getQuantity() == null ? 0 : order.getQuantity()));
        vo.setStatus(order.getStatus());
        vo.setStatusText("待确认");
        vo.setCreateTime(order.getCreateTime());
        applyOrderAuditor(vo, order.getTenantCode(), ORDER_TYPE_PRODUCTION, order.getOrderId());
        return vo;
    }

    private SalesOrder findPendingSalesOrder(String orderId) {
        SalesOrder order = salesOrderMapper.selectOne(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getTenantCode, TenantPermissionContext.getTenantCode())
                .eq(SalesOrder::getOrderId, orderId)
                .in(SalesOrder::getStatus,
                        OrderStatusEnum.PENDING_CONFIRM.getCode(),
                        OrderStatusEnum.PENDING_PAY.getCode()));
        if (order == null) {
            throw new BusinessException("待审批销售订单不存在或已处理");
        }
        return order;
    }

    private ProductionOrder findPendingProductionOrder(String orderId) {
        ProductionOrder order = productionOrderMapper.selectOne(new LambdaQueryWrapper<ProductionOrder>()
                .eq(ProductionOrder::getTenantCode, TenantPermissionContext.getTenantCode())
                .eq(ProductionOrder::getOrderId, orderId)
                .eq(ProductionOrder::getStatus, OrderStatusEnum.PENDING_CONFIRM.getCode()));
        if (order == null) {
            throw new BusinessException("待确认生产订单不存在或已处理");
        }
        return order;
    }

    private long countPendingOrders(String tenantCode, Long currentUserId) {
        if (currentUserId == null) {
            return 0L;
        }
        long count = 0L;
        List<SalesOrder> salesOrders = salesOrderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getTenantCode, tenantCode)
                .in(SalesOrder::getStatus,
                        OrderStatusEnum.PENDING_CONFIRM.getCode(),
                        OrderStatusEnum.PENDING_PAY.getCode()));
        for (SalesOrder order : salesOrders) {
            Long auditorId = ensureOrderAuditorId(order.getTenantCode(), ORDER_TYPE_SALES, order.getOrderId());
            if (currentUserId.equals(auditorId)) {
                count++;
            }
        }
        List<ProductionOrder> productionOrders = productionOrderMapper.selectList(new LambdaQueryWrapper<ProductionOrder>()
                .eq(ProductionOrder::getTenantCode, tenantCode)
                .eq(ProductionOrder::getStatus, OrderStatusEnum.PENDING_CONFIRM.getCode()));
        for (ProductionOrder order : productionOrders) {
            Long auditorId = ensureOrderAuditorId(order.getTenantCode(), ORDER_TYPE_PRODUCTION, order.getOrderId());
            if (currentUserId.equals(auditorId)) {
                count++;
            }
        }
        return count;
    }

    private void applyOrderAuditor(OrderApprovalVO vo, String tenantCode, String orderType, String orderId) {
        Long auditorId = ensureOrderAuditorId(tenantCode, orderType, orderId);
        vo.setAuditorId(auditorId);
        vo.setAuditorName(resolveOrderAuditorName(auditorId));
        vo.setCanAudit(canCurrentUserAudit(TenantPermissionContext.getUserId(), auditorId, null));
    }

    private Long ensureOrderAuditorId(String tenantCode, String orderType, String orderId) {
        String approvalCode = orderApprovalCode(orderType, orderId);
        List<Long> activeAuditors = approvalAuditorCandidateService.findActiveAuditorIds(
                tenantCode, APPROVAL_TYPE_ORDER, approvalCode);
        if (activeAuditors != null && !activeAuditors.isEmpty()) {
            Long existingAuditorId = activeAuditors.get(0);
            if (existingAuditorId != null && existingAuditorId > 0) {
                return existingAuditorId;
            }
        }
        Long auditorId = approvalDefaultAuditorService.resolveAuditorId(
                tenantCode,
                APPROVAL_TYPE_ORDER,
                null,
                null,
                resolveOrderAuditPermissionCode(orderType),
                false
        );
        approvalAuditorCandidateService.replaceActiveCandidates(
                tenantCode, APPROVAL_TYPE_ORDER, approvalCode, List.of(auditorId));
        return auditorId;
    }

    private void validateCurrentOrderAuditor(String orderType, String orderId, String tenantCode) {
        Long auditorId = ensureOrderAuditorId(tenantCode, orderType, orderId);
        if (!canCurrentUserAudit(TenantPermissionContext.getUserId(), auditorId, null)) {
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

    private String resolveOrderAuditorName(Long auditorId) {
        if (auditorId == null || auditorId <= 0) {
            return "待分配";
        }
        User auditor = userMapper.selectById(auditorId);
        return auditor == null || !StringUtils.hasText(auditor.getName()) ? "待分配" : auditor.getName();
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

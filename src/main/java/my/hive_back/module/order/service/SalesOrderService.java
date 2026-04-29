package my.hive_back.module.order.service;

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
import my.hive_back.common.utils.CodeGeneratorUtil;
import my.hive_back.module.order.IsInvoiceEnum;
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
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
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

    @RequirePermission(value = "sales:order:list", message = "您没有权限查询销售订单列表")
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
        // 关键词：订单号/客户名 模糊查询（OR 关系）
        String keyWord = request.getKeyWord();
        if (StringUtils.isNotBlank(keyWord)) {
            queryWrapper.and(wrapper -> wrapper
                    .like(SalesOrder::getOrderId, keyWord)
                    .or()
                    .like(SalesOrder::getCustomerName, keyWord)
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

    private long safePageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? DEFAULT_PAGE_NUM : pageNum;
    }

    private long safePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * 根据订单ID查询订单详情 (返回 VO 对象)
     */
    @RequirePermission(value = "sales:order:detail", message = "您没有权限查询销售订单详情")
    public SalesOrderVO getByIdandTenantId(String orderId) {
        // 1. 查询主表订单信息
        SalesOrder order = salesOrderMapper.selectByOrderId(orderId);
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

    @RequirePermission(value = "sales:order:detail", message = "您没有权限查询销售订单状态变更日志")
    public List<SalesOrderStatusLog> selectSalesOrderStatusLog(@NotBlank String orderId) {
        return salesOrderStatusLogMapper.selectList(new LambdaQueryWrapper<SalesOrderStatusLog>()
                .eq(SalesOrderStatusLog::getOrderId, orderId)
                .orderByAsc(SalesOrderStatusLog::getCreateTime));
    }


    @Transactional(rollbackFor = Exception.class)
    @RequirePermission(value = "sales:order:status", message = "您没有权限添加销售订单")
    public void addSalesOrder(@Valid SalesOrderAddRequest request) {
        SalesOrder order = new SalesOrder();

        Integer createProductionOrder = request.getCreateProductionOrder();

        BeanUtils.copyProperties(request, order);
        String orderId = codeGeneratorUtil.generateSalesOrderCode();
        order.setOrderId(orderId);
        order.setIsInvoice(IsInvoiceEnum.NO.getCode());
        order.setTenantCode(TenantPermissionContext.getTenantCode());
        order.setStatus(OrderStatusEnum.PENDING_CONFIRM.getCode());
        order.setCreator(resolveCurrentUserIdText());
        order.setUpdater(resolveCurrentUserIdText());
        order.setGoodsDesc(buildGoodsDesc(request.getItems()));
        order.setTotalAmount(BigDecimal.ZERO);
        order.setTotalQuantity(sumSalesQuantity(request.getItems()));
        salesOrderMapper.insert(order);
        insertSalesStatusLog(order, null, order.getStatus(), "create", "创建销售订单");


        request.getItems().forEach(item -> {
            SalesOrderDetail detail = new SalesOrderDetail();
            detail.setOrderId(order.getOrderId());
            detail.setTenantCode(TenantPermissionContext.getTenantCode());
            BeanUtils.copyProperties(item, detail);
            salesOrderDetailMapper.insert(detail);
        });

        // 需要创建生产订单
        if (createProductionOrder == 1) {
            request.getItems().forEach(item -> {
                ProductionOrderAddRequest productionOrderRequest = new ProductionOrderAddRequest();
                BeanUtils.copyProperties(request, productionOrderRequest);
                BeanUtils.copyProperties(item, productionOrderRequest);
                productionOrderService.addProductionOrder(productionOrderRequest, order.getOrderId());
            });
        }


    }

    private String buildGoodsDesc(List<SalesOrderAddRequest.OrderItemDTO> items) {
        return items.stream()
                .map(item -> item.getModelCode().trim() + " / " + numberText(item.getWeight()) + "克 / " + numberText(item.getSpec()) + "规格 × " + item.getQuantity().stripTrailingZeros().toPlainString())
                .collect(Collectors.joining("；"));
    }

    private Integer sumSalesQuantity(List<SalesOrderAddRequest.OrderItemDTO> items) {
        BigDecimal total = items.stream()
                .map(SalesOrderAddRequest.OrderItemDTO::getQuantity)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.intValue();
    }

    private String numberText(Number value) {
        if (value == null) {
            return "";
        }
        return BigDecimal.valueOf(value.doubleValue()).stripTrailingZeros().toPlainString();
    }

    @Transactional(rollbackFor = Exception.class)
    @RequirePermission(value = "sales:order:status", message = "您没有权限更新销售订单状态")
    public SalesOrder updateStatusAndProcess(@NotBlank String orderId, @Valid SalesOrderUpdateRequest request) {
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

        // 2. 核心业务逻辑：状态与物流信息校验
        // 如果目标状态是“已发货 (shipped)”，强制要求填写完整的物流信息
        if (OrderStatusEnum.SHIPPED.getCode().equals(targetStatus)) {
            SalesOrderUpdateRequest.ExpressInfo expressInfo = request.getExpressInfo();
            if (expressInfo == null
                    || StringUtils.isBlank(expressInfo.getExpressCompany())
                    || StringUtils.isBlank(expressInfo.getExpressNo())) {
                throw new BusinessException(400, "发货操作必须填写完整的物流公司和物流单号");
            }
            // 填入物流信息
            order.setExpressCompany(expressInfo.getExpressCompany());
            order.setExpressNo(expressInfo.getExpressNo());
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
            insertSalesStatusLog(order, oldStatus, order.getStatus(), "status_change", "小程序更新销售订单状态");
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
}

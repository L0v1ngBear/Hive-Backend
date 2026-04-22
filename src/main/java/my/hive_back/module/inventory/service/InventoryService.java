package my.hive_back.module.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import my.hive.common.annotation.RequirePermission;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive_back.common.utils.BarCodeUtil;
import my.hive_back.common.utils.CodeGeneratorUtil;
import my.hive_back.common.utils.RedisUtil;
import my.hive_back.module.inventory.InventoryInTypeEnum;
import my.hive_back.module.inventory.InventoryOperateTypeEnum;
import my.hive_back.module.inventory.mapper.ClothMapper;
import my.hive_back.module.inventory.mapper.ClothModelSpecMapper;
import my.hive_back.module.inventory.mapper.InventoryRecordMapper;
import my.hive_back.module.inventory.mapper.OutboundItemMapper;
import my.hive_back.module.inventory.mapper.OutboundOrderMapper;
import my.hive_back.module.inventory.model.dto.InventoryInRequest;
import my.hive_back.module.inventory.model.dto.InventoryOutRequest;
import my.hive_back.module.inventory.model.entity.Cloth;
import my.hive_back.module.inventory.model.entity.ClothModelSpec;
import my.hive_back.module.inventory.model.entity.InventoryRecord;
import my.hive_back.module.inventory.model.entity.OutboundItem;
import my.hive_back.module.inventory.model.entity.OutboundOrder;
import my.hive_back.module.inventory.model.vo.ClothInfoVO;
import my.hive_back.module.inventory.model.vo.InventoryRecordVO;
import my.hive_back.module.inventory.model.vo.OutboundOrderOptionVO;
import my.hive_back.module.price.mapper.PriceSkuMapper;
import my.hive_back.module.statics.inventory.mapper.InventoryTrendStaticsMapper;
import my.hive_back.module.statics.inventory.model.entity.InventoryTrendStatics;
import my.hive_back.module.statics.inventory.model.vo.InventoryTrendVO;
import my.hive_back.module.order.mapper.SalesOrderMapper;
import my.hive_back.module.order.model.entity.SalesOrder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
/**
 * InventoryService 属于小程序后端库存模块，实现核心业务编排与规则逻辑。
 */
@Slf4j
@Service
public class InventoryService {

    private static final String CLOTH_OUT_LOCK_PREFIX = "lock:cloth:out:";
    private static final String OUTBOUND_ORDER_LOCK_PREFIX = "lock:outbound:order:";
    private static final int OUTBOUND_MAX_RETRY = 3;
    private static final float WARNING_METERS_THRESHOLD = 5F;

    @Resource
    private InventoryTrendStaticsMapper staticsMapper;
    @Resource
    private InventoryRecordMapper inventoryRecordMapper;
    @Resource
    private ClothModelSpecMapper clothModelSpecMapper;
    @Resource
    private BarCodeUtil barCodeUtil;
    @Resource
    private ClothMapper clothMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedisUtil redisUtil;
    @Resource
    private OutboundOrderMapper outboundOrderMapper;
    @Resource
    private CodeGeneratorUtil codeGeneratorUtil;
    @Resource
    private OutboundItemMapper outboundItemMapper;
    @Resource
    private PriceSkuMapper priceSkuMapper;
    @Resource
    private SalesOrderMapper salesOrderMapper;

    @Value("${redis.key-prefix.trend.today_in}")
    private String REDIS_TODAY_IN;
    @Value("${redis.key-prefix.trend.today_out}")
    private String REDIS_TODAY_OUT;

    @Transactional(rollbackFor = Exception.class)
    @RequirePermission(value = "inventory:in", message = "您没有权限执行布匹入库")
    public ClothInfoVO inCloth(@Valid InventoryInRequest inventoryInRequest) {
        InventoryInTypeEnum inTypeEnum = InventoryInTypeEnum.getCode(inventoryInRequest.getInType());
        String barcode;

        switch (inTypeEnum) {
            case SCAN -> barcode = inventoryInRequest.getBarcode();
            case HAND, AUTO -> {
                barcode = barCodeUtil.createBarCode(TenantPermissionContext.getTenantCode());
                inventoryInRequest.setBarcode(barcode);
                inventoryHandIn(inventoryInRequest);
            }
            default -> throw new BusinessException("不支持的入库类型");
        }

        saveClothModelSpecAsync(inventoryInRequest.getModelCode(), inventoryInRequest.getSpec(), TenantPermissionContext.getTenantCode());

        ClothInfoVO clothInfoVO = new ClothInfoVO();
        BeanUtils.copyProperties(inventoryInRequest, clothInfoVO);
        clothInfoVO.setBarcode(barcode);
        return clothInfoVO;
    }

    @RequirePermission(value = "inventory:out", message = "您没有权限执行布匹出库")
    @Transactional(rollbackFor = Exception.class)
    public ClothInfoVO outCloth(@Valid InventoryOutRequest request) {
        String tenantCode = TenantPermissionContext.getTenantCode();
        Long userId = TenantPermissionContext.getUserId();
        String barCode = request.getBarcode();
        String businessOrderNo = request.getOrderNo();
        String requestId = normalizeRequestId(request.getRequestId());

        if (!StringUtils.isNotBlank(barCode)) {
            throw new BusinessException("条码不能为空");
        }
        if (!StringUtils.isNotBlank(businessOrderNo)) {
            throw new BusinessException("业务单号不能为空");
        }

        ClothInfoVO idempotentResult = findIdempotentOutboundResult(tenantCode, requestId);
        if (idempotentResult != null) {
            return idempotentResult;
        }

        String lockKey = CLOTH_OUT_LOCK_PREFIX + tenantCode + ":" + barCode;
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, 30, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(locked)) {
            throw new BusinessException("该布匹正在出库处理中，请稍后重试");
        }

        try {
            ClothInfoVO afterLockResult = findIdempotentOutboundResult(tenantCode, requestId);
            if (afterLockResult != null) {
                return afterLockResult;
            }

            Cloth cloth = selectClothByBarCode(barCode);
            if (cloth == null) {
                throw new BusinessException("布匹不存在");
            }

            Float metersToOut = request.getMeters();
            if (metersToOut == null || metersToOut <= 0) {
                metersToOut = cloth.getRemainingMeters();
            }
            if (metersToOut == null || metersToOut <= 0) {
                throw new BusinessException("出库米数必须大于0");
            }

            Cloth latestCloth = deductClothMeters(cloth, metersToOut, userId);
            saveInventoryOutRecord(latestCloth, metersToOut, userId);
            OutboundOrder order = getOrCreatePendingOutboundOrder(tenantCode, businessOrderNo, request.getCustomerName(), userId);
            saveOutboundItem(order.getId(), latestCloth, metersToOut, tenantCode, requestId);
            asyncRefreshTrendCache(barCode, tenantCode, metersToOut, REDIS_TODAY_OUT);

            ClothInfoVO clothInfoVO = new ClothInfoVO();
            BeanUtils.copyProperties(latestCloth, clothInfoVO);
            clothInfoVO.setMeters(latestCloth.getRemainingMeters());
            return clothInfoVO;
        } finally {
            safeReleaseLock(lockKey, lockValue);
        }
    }

    private String normalizeRequestId(String requestId) {
        return StringUtils.isBlank(requestId) ? null : requestId.trim();
    }

    private ClothInfoVO findIdempotentOutboundResult(String tenantCode, String requestId) {
        if (!StringUtils.isNotBlank(requestId)) {
            return null;
        }
        OutboundItem item = outboundItemMapper.selectOne(new LambdaQueryWrapper<OutboundItem>()
                .eq(OutboundItem::getRequestId, requestId)
                .last("limit 1"));
        if (item == null) {
            return null;
        }
        Cloth cloth = selectClothByBarCode(item.getBarcode());
        if (cloth == null) {
            return null;
        }
        ClothInfoVO vo = new ClothInfoVO();
        BeanUtils.copyProperties(cloth, vo);
        vo.setMeters(cloth.getRemainingMeters());
        return vo;
    }

    private Cloth deductClothMeters(Cloth originalCloth, Float metersToOut, Long userId) {
        for (int retry = 0; retry < OUTBOUND_MAX_RETRY; retry++) {
            Cloth current = clothMapper.selectById(originalCloth.getId());
            if (current == null) {
                throw new BusinessException("布匹不存在或已被删除");
            }
            Float currentRemaining = current.getRemainingMeters();
            if (currentRemaining == null || currentRemaining <= 0) {
                throw new BusinessException("该布匹已全部出库");
            }
            if (currentRemaining < metersToOut) {
                throw new BusinessException("剩余米数不足，无法完成本次出库");
            }

            float newRemaining = currentRemaining - metersToOut;
            current.setRemainingMeters(newRemaining);
            current.setOutTime(LocalDateTime.now());
            current.setOutOperatorId(userId);
            current.setStatus(newRemaining == 0F ? InventoryOperateTypeEnum.OUT.getCode() : InventoryOperateTypeEnum.PART_OUT.getCode());

            int updatedRows = clothMapper.updateById(current);
            if (updatedRows > 0) {
                return current;
            }
        }
        throw new BusinessException("出库冲突，请刷新后重试");
    }

    private void saveInventoryOutRecord(Cloth cloth, Float metersToOut, Long userId) {
        InventoryRecord record = new InventoryRecord();
        record.setTenantCode(cloth.getTenantCode());
        record.setClothId(cloth.getId());
        record.setModelCode(cloth.getModelCode());
        record.setOperatorId(userId);
        record.setOperateType(InventoryOperateTypeEnum.OUT.getCode());
        record.setOperateMeters(metersToOut);
        record.setRemainingMeters(cloth.getRemainingMeters());
        inventoryRecordMapper.insert(record);
    }

    private OutboundOrder getOrCreatePendingOutboundOrder(String tenantCode, String businessOrderNo, String customerName, Long userId) {
        String lockKey = OUTBOUND_ORDER_LOCK_PREFIX + tenantCode + ":" + businessOrderNo;
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, 15, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(locked)) {
            throw new BusinessException("当前业务单正在结转出库单，请稍后再试");
        }
        try {
            OutboundOrder order = outboundOrderMapper.selectOne(new LambdaQueryWrapper<OutboundOrder>()
                    .eq(OutboundOrder::getBizOrderNo, businessOrderNo)
                    .eq(OutboundOrder::getPrintStatus, 0)
                    .last("limit 1"));
            if (order != null) {
                return order;
            }

            OutboundOrder newOrder = new OutboundOrder();
            newOrder.setTenantCode(tenantCode);
            newOrder.setOrderNo(codeGeneratorUtil.generateOutboundOrderNo());
            newOrder.setBizOrderNo(businessOrderNo);
            newOrder.setCustomerName(customerName);
            newOrder.setOrderStatus(0);
            newOrder.setPrintStatus(0);
            newOrder.setOperatorId(userId);
            newOrder.setCreateTime(LocalDateTime.now());
            outboundOrderMapper.insert(newOrder);
            return newOrder;
        } finally {
            safeReleaseLock(lockKey, lockValue);
        }
    }

    private void saveOutboundItem(Long orderId, Cloth cloth, Float metersToOut, String tenantCode, String requestId) {
        OutboundItem item = new OutboundItem();
        item.setTenantCode(tenantCode);
        item.setOrderId(orderId);
        item.setBarcode(cloth.getBarcode());
        item.setModelCode(cloth.getModelCode());
        item.setSpec(cloth.getSpec());
        item.setMeters(metersToOut);
        item.setRequestId(requestId);
        BigDecimal price = priceSkuMapper.getPrice(tenantCode, cloth.getModelCode());
        if (price == null) {
            price = BigDecimal.ZERO;
        }
        item.setPrice(price);
        item.setTotalAmount(price.multiply(BigDecimal.valueOf(metersToOut)));
        try {
            outboundItemMapper.insert(item);
        } catch (DuplicateKeyException ex) {
            if (StringUtils.isNotBlank(requestId)) {
                log.warn("检测到重复出库请求, requestId={}", requestId);
                return;
            }
            throw ex;
        }
    }

    private void safeReleaseLock(String lockKey, String lockValue) {
        try {
            String currentValue = stringRedisTemplate.opsForValue().get(lockKey);
            if (lockValue.equals(currentValue)) {
                stringRedisTemplate.delete(lockKey);
            }
        } catch (Exception e) {
            log.error("释放出库锁失败, lockKey={}", lockKey, e);
        }
    }

    @Async
    public void asyncRefreshTrendCache(String barCode, String tenantCode, Float meters, String staticsPrefix) {
        try {
            String key = staticsPrefix + tenantCode + ":" + LocalDate.now();
            stringRedisTemplate.opsForValue().increment(key, meters.doubleValue());
            stringRedisTemplate.expire(key, redisUtil.getSecondsToAfterDays(2), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("刷新库存趋势缓存失败, barcode: {}", barCode, e);
        }
    }

    @Async
    public void saveClothModelSpecAsync(String modelCode, Float spec, String tenantCode) {
        ClothModelSpec specEntity = new ClothModelSpec();
        specEntity.setModelCode(modelCode);
        specEntity.setSpec(spec);
        specEntity.setTenantCode(tenantCode);
        try {
            clothModelSpecMapper.insert(specEntity);
        } catch (DuplicateKeyException ignored) {
        }
    }

    private void inventoryHandIn(InventoryInRequest inventoryInRequest) {
        Cloth cloth = new Cloth();
        BeanUtils.copyProperties(inventoryInRequest, cloth);
        cloth.setInOperatorId(TenantPermissionContext.getUserId());
        cloth.setInTime(LocalDateTime.now());
        cloth.setTotalMeters(inventoryInRequest.getMeters());
        cloth.setRemainingMeters(inventoryInRequest.getMeters());
        cloth.setStatus(InventoryOperateTypeEnum.IN.getCode());
        cloth.setTenantCode(TenantPermissionContext.getTenantCode());
        clothMapper.insert(cloth);

        InventoryRecord record = new InventoryRecord();
        record.setClothId(cloth.getId());
        record.setModelCode(inventoryInRequest.getModelCode());
        record.setTenantCode(TenantPermissionContext.getTenantCode());
        record.setOperatorId(TenantPermissionContext.getUserId());
        record.setOperateType(InventoryOperateTypeEnum.IN.getCode());
        record.setOperateMeters(inventoryInRequest.getMeters());
        record.setRemainingMeters(inventoryInRequest.getMeters());
        inventoryRecordMapper.insert(record);

        String key = REDIS_TODAY_IN + TenantPermissionContext.getTenantCode() + ":" + LocalDate.now();
        stringRedisTemplate.opsForValue().increment(key, inventoryInRequest.getMeters().doubleValue());
        stringRedisTemplate.expire(key, redisUtil.getSecondsToAfterDays(2), TimeUnit.SECONDS);
    }

    public Cloth selectClothByBarCode(String barCode) {
        return clothMapper.selectOne(new LambdaQueryWrapper<Cloth>()
                .eq(Cloth::getBarcode, barCode));
    }

    public List<ClothModelSpec> searchModelSpec(String keyword) {
        LambdaQueryWrapper<ClothModelSpec> queryWrapper = new LambdaQueryWrapper<>();
        if (!StringUtils.isBlank(keyword)) {
            queryWrapper.like(ClothModelSpec::getModelCode, keyword);
        }
        return clothModelSpecMapper.selectList(queryWrapper);
    }

    public List<OutboundOrderOptionVO> searchOutboundBizOrders(String keyword) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        LambdaQueryWrapper<SalesOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(SalesOrder::getStatus, List.of("pending_ship", "shipped"))
                .and(StringUtils.isNotBlank(safeKeyword), wrapper -> wrapper
                        .like(SalesOrder::getOrderId, safeKeyword)
                        .or()
                        .like(SalesOrder::getCustomerName, safeKeyword)
                        .or()
                        .like(SalesOrder::getProjectName, safeKeyword))
                .orderByDesc(SalesOrder::getUpdateTime)
                .last("limit 10");
        return salesOrderMapper.selectList(queryWrapper).stream().map(order -> {
            OutboundOrderOptionVO vo = new OutboundOrderOptionVO();
            vo.setOrderNo(order.getOrderId());
            vo.setCustomerName(order.getCustomerName());
            vo.setProjectName(order.getProjectName());
            return vo;
        }).toList();
    }

    public List<InventoryRecord> getUserRecentRecord() {
        Long userId = TenantPermissionContext.getUserId();
        Page<InventoryRecord> page = new Page<>(1, 7);
        page.setSearchCount(false);
        LambdaQueryWrapper<InventoryRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(InventoryRecord::getOperatorId, userId)
                .orderByDesc(InventoryRecord::getCreateTime);
        return inventoryRecordMapper.selectPage(page, queryWrapper).getRecords();
    }

    public List<InventoryRecordVO> warningList() {
        List<Cloth> list = clothMapper.selectList(new LambdaQueryWrapper<Cloth>()
                .ne(Cloth::getStatus, InventoryOperateTypeEnum.OUT.getCode())
                .le(Cloth::getRemainingMeters, WARNING_METERS_THRESHOLD)
                .orderByAsc(Cloth::getRemainingMeters)
                .last("limit 10"));
        return list.stream().map(item -> {
            InventoryRecordVO vo = new InventoryRecordVO();
            vo.setId(item.getId());
            vo.setModelCode(item.getModelCode());
            vo.setMeters(item.getRemainingMeters());
            vo.setCreateTime(item.getUpdateTime());
            return vo;
        }).toList();
    }

    public InventoryTrendVO getLastWeekTrend() {
        InventoryTrendVO vo = new InventoryTrendVO();
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        LocalDateTime dbEndDate = now.minusDays(1);
        LocalDateTime dbStartDate = now.minusDays(6);
        LambdaQueryWrapper<InventoryTrendStatics> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(InventoryTrendStatics::getStatDate, dbStartDate, dbEndDate);
        wrapper.orderByAsc(InventoryTrendStatics::getStatDate);
        List<InventoryTrendStatics> dbList = staticsMapper.selectList(wrapper);

        String tenantCode = TenantPermissionContext.getTenantCode();
        Float todayIn = getTrendMeters(REDIS_TODAY_IN, tenantCode, now.toLocalDate());
        Float todayOut = getTrendMeters(REDIS_TODAY_OUT, tenantCode, now.toLocalDate());

        List<String> dateList = new ArrayList<>();
        List<Float> inList = new ArrayList<>();
        List<Float> outList = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {
            LocalDateTime date = now.minusDays(i);
            LocalDate currentDay = date.toLocalDate();
            String dateStr = date.format(formatter);
            dateList.add(dateStr);
            if (i == 0) {
                inList.add(todayIn == null ? 0f : todayIn);
                outList.add(todayOut == null ? 0f : todayOut);
            } else {
                InventoryTrendStatics stat = dbList.stream()
                        .filter(item -> item.getStatDate().toLocalDate().equals(currentDay))
                        .findFirst().orElse(null);
                inList.add(stat == null ? 0f : stat.getDayInMeters());
                outList.add(stat == null ? 0f : stat.getDayOutMeters());
            }
        }

        vo.setDates(dateList);
        vo.setInMeters(inList);
        vo.setOutMeters(outList);
        return vo;
    }

    private Float getTrendMeters(String keyPrefix, String tenantCode, LocalDate statDate) {
        try {
            String value = stringRedisTemplate.opsForValue().get(keyPrefix + tenantCode + ":" + statDate);
            return value == null ? 0f : Float.parseFloat(value);
        } catch (Exception ex) {
            log.warn("读取库存趋势缓存失败，tenantCode: {}, statDate: {}", tenantCode, statDate, ex);
            return 0f;
        }
    }

    public void finishOutbound(String orderNo) {
        submitOutboundToPrint(orderNo);
    }

    public void submitOutboundToPrint(String orderNo) {
        OutboundOrder order = outboundOrderMapper.selectOne(new LambdaQueryWrapper<OutboundOrder>()
                .and(wrapper -> wrapper.eq(OutboundOrder::getOrderNo, orderNo).or().eq(OutboundOrder::getBizOrderNo, orderNo))
                .eq(OutboundOrder::getPrintStatus, 0)
                .in(OutboundOrder::getOrderStatus, List.of(0, 1))
                .orderByDesc(OutboundOrder::getId)
                .last("limit 1"));
        if (order == null) {
            throw new BusinessException("出库单不存在或已打印");
        }

        LambdaUpdateWrapper<OutboundOrder> luw = new LambdaUpdateWrapper<>();
        luw.eq(OutboundOrder::getId, order.getId())
                .eq(OutboundOrder::getPrintStatus, 0)
                .in(OutboundOrder::getOrderStatus, 0, 1)
                .set(OutboundOrder::getOrderStatus, 1)
                .set(OutboundOrder::getUpdateTime, LocalDateTime.now());
        int rows = outboundOrderMapper.update(null, luw);
        if (rows == 0) {
            throw new BusinessException("出库单不存在或已打印");
        }
    }
}

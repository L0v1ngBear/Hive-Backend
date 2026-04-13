package my.hive_back.module.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import my.hive_back.common.annotation.RequirePermission;
import my.hive_back.common.context.TenantPermissionContext;
import my.hive_back.common.exception.BusinessException;
import my.hive_back.common.utils.BarCodeUtil;
import my.hive_back.common.utils.CodeGeneratorUtil;
import my.hive_back.common.utils.RedisUtil;
import my.hive_back.module.inventory.InventoryInTypeEnum;
import my.hive_back.module.inventory.InventoryOperateTypeEnum;
import my.hive_back.module.inventory.mapper.*;
import my.hive_back.module.inventory.model.dto.InventoryInRequest;
import my.hive_back.module.inventory.model.dto.InventoryOutRequest;
import my.hive_back.module.inventory.model.entity.Cloth;
import my.hive_back.module.inventory.model.entity.ClothModelSpec;
import my.hive_back.module.inventory.model.entity.InventoryRecord;
import my.hive_back.module.inventory.model.entity.OutboundItem;
import my.hive_back.module.inventory.model.entity.OutboundOrder;
import my.hive_back.module.inventory.model.vo.ClothInfoVO;
import my.hive_back.module.price.mapper.PriceSkuMapper;
import my.hive_back.module.statics.inventory.mapper.InventoryTrendStaticsMapper;
import my.hive_back.module.statics.inventory.model.entity.InventoryTrendStatics;
import my.hive_back.module.statics.inventory.model.vo.InventoryTrendVO;
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
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class InventoryService {

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

    @Value("${redis.key-prefix.trend.today_in}")
    private String REDIS_TODAY_IN;
    @Value("${redis.key-prefix.trend.today_out}")
    private String REDIS_TODAY_OUT;

    private static final String INVENTORY_STATICS_IN_KEY_PREFIX = "inventory:in:statics:";
    private static final String INVENTORY_STATICS_OUT_KEY_PREFIX = "inventory:out:statics:";
    private static final String CLOTH_OUT_LOCK_PREFIX = "lock:cloth:out:";

    /**
     * 布匹入库。
     */
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission(value = "inventory:in", message = "您没有权限执行布匹入库")
    public ClothInfoVO inCloth(@Valid InventoryInRequest inventoryInRequest) {
        InventoryInTypeEnum inTypeEnum = InventoryInTypeEnum.getCode(inventoryInRequest.getInType());
        String barcode;

        // 扫码入库直接使用传入条码，手动和自动入库由系统生成条码。
        switch (inTypeEnum) {
            case SCAN -> barcode = inventoryInRequest.getBarcode();
            case HAND, AUTO -> {
                barcode = barCodeUtil.createBarCode(TenantPermissionContext.getTenantCode());
                inventoryInRequest.setBarcode(barcode);
                inventoryHandIn(inventoryInRequest);
            }
            default -> throw new BusinessException("不支持的入库类型");
        }

        // 异步沉淀型号和幅宽组合，方便后续入库时搜索复用。
        saveClothModelSpecAsync(inventoryInRequest.getModelCode(), inventoryInRequest.getSpec(), TenantPermissionContext.getTenantCode());

        ClothInfoVO clothInfoVO = new ClothInfoVO();
        BeanUtils.copyProperties(inventoryInRequest, clothInfoVO);
        return clothInfoVO;
    }

    /**
     * 布匹出库。出库时会创建或追加待打印出库单明细，PC 管理端负责最终打印。
     */
    @RequirePermission(value = "inventory:out", message = "您没有权限执行布匹出库")
    @Transactional(rollbackFor = Exception.class)
    public ClothInfoVO outCloth(@Valid InventoryOutRequest request) {
        String barCode = request.getBarcode();
        String orderNo = request.getOrderNo();
        String customerName = request.getCustomerName();
        String tenantCode = TenantPermissionContext.getTenantCode();
        Long userId = TenantPermissionContext.getUserId();

        if (barCode == null) {
            throw new BusinessException("条码不能为空");
        }

        String lockKey = CLOTH_OUT_LOCK_PREFIX + tenantCode + ":" + barCode;

        // 同一租户同一条码短时间只允许一个出库操作，避免重复扣减库存。
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 5, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(locked)) {
            throw new BusinessException("该布匹正在出库处理中，请稍后再试");
        }

        try {
            Cloth cloth = selectClothByBarCode(barCode);
            if (cloth == null) {
                throw new BusinessException("布匹不存在");
            }

            Float metersToOut = request.getMeters();

            // 指定米数时执行部分出库；未指定米数时整卷出库。
            if (metersToOut != null && metersToOut > 0) {
                LambdaUpdateWrapper<Cloth> luw = new LambdaUpdateWrapper<>();
                luw.eq(Cloth::getBarcode, barCode)
                        .eq(Cloth::getTenantCode, tenantCode)
                        .ge(Cloth::getRemainingMeters, metersToOut)
                        .setSql("remaining_meters = remaining_meters - " + metersToOut)
                        .set(Cloth::getOutTime, LocalDateTime.now())
                        .set(Cloth::getOutOperatorId, userId)
                        .setSql("status = CASE WHEN remaining_meters = " + metersToOut + " THEN " + InventoryOperateTypeEnum.OUT.getCode()
                                + " ELSE " + InventoryOperateTypeEnum.PART_OUT.getCode() + " END");

                int rows = clothMapper.update(luw);
                if (rows == 0) {
                    throw new BusinessException("库存不足或布匹状态已变化，请刷新后重试");
                }
                cloth.setRemainingMeters(cloth.getRemainingMeters() - metersToOut);
            } else {
                metersToOut = cloth.getRemainingMeters();
                LambdaUpdateWrapper<Cloth> luw = new LambdaUpdateWrapper<>();
                luw.eq(Cloth::getBarcode, barCode)
                        .eq(Cloth::getTenantCode, tenantCode)
                        .set(Cloth::getRemainingMeters, 0)
                        .set(Cloth::getStatus, InventoryOperateTypeEnum.OUT.getCode())
                        .set(Cloth::getOutTime, LocalDateTime.now())
                        .set(Cloth::getOutOperatorId, userId);
                clothMapper.update(luw);
                cloth.setRemainingMeters(0F);
            }

            // 出库单打印协同：小程序只追加待打印明细，PC 管理端打印成功后再标记已打印。
            LambdaQueryWrapper<OutboundOrder> orderQuery = new LambdaQueryWrapper<>();
            orderQuery.eq(OutboundOrder::getTenantCode, tenantCode)
                    .eq(OutboundOrder::getOrderNo, orderNo)
                    .eq(OutboundOrder::getPrintStatus, 0)
                    .last("LIMIT 1");

            OutboundOrder order = outboundOrderMapper.selectOne(orderQuery);

            if (order == null) {
                order = new OutboundOrder();
                order.setTenantCode(tenantCode);
                order.setOrderNo(codeGeneratorUtil.generateOutboundOrderNo());
                order.setCustomerName(customerName);
                order.setOrderStatus(0);
                order.setPrintStatus(0);
                order.setOperatorId(userId);
                order.setCreateTime(LocalDateTime.now());
                outboundOrderMapper.insert(order);
            }

            OutboundItem item = new OutboundItem();
            item.setTenantCode(tenantCode);
            item.setOrderId(order.getId());
            item.setBarcode(cloth.getBarcode());
            item.setModelCode(cloth.getModelCode());
            item.setSpec(cloth.getSpec());
            item.setMeters(metersToOut);

            BigDecimal price = priceSkuMapper.getPrice(tenantCode, cloth.getModelCode());
            item.setPrice(price);
            item.setTotalAmount(price.multiply(BigDecimal.valueOf(metersToOut)));
            outboundItemMapper.insert(item);

            asyncLogAndStatics(barCode, tenantCode, userId, metersToOut, INVENTORY_STATICS_OUT_KEY_PREFIX);

            ClothInfoVO clothInfoVO = new ClothInfoVO();
            BeanUtils.copyProperties(cloth, clothInfoVO);
            clothInfoVO.setMeters(cloth.getRemainingMeters());
            return clothInfoVO;
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    /**
     * 异步写入库存操作记录，并累计当天出入库趋势数据。
     */
    @Async
    public void asyncLogAndStatics(String barCode, String tenantCode, Long userId, Float meters, String staticsPrefix) {
        try {
            Cloth cloth = clothMapper.selectOne(new LambdaQueryWrapper<Cloth>()
                    .eq(Cloth::getBarcode, barCode)
                    .eq(Cloth::getTenantCode, tenantCode));
            if (cloth == null) {
                return;
            }

            InventoryRecord record = new InventoryRecord();
            record.setTenantCode(tenantCode);
            record.setClothId(cloth.getId());
            record.setOperatorId(userId);
            record.setOperateType(InventoryOperateTypeEnum.OUT.getCode());
            record.setOperateMeters(meters);
            record.setRemainingMeters(cloth.getRemainingMeters());
            inventoryRecordMapper.insert(record);

            String key = staticsPrefix + tenantCode + ":" + LocalDate.now();
            stringRedisTemplate.opsForValue().increment(key, meters.doubleValue());
            stringRedisTemplate.expire(key, redisUtil.getSecondsToNextDay(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("异步写入库存记录或统计失败，barcode: {}", barCode, e);
        }
    }

    /**
     * 保存布匹型号和幅宽组合，已存在时忽略。
     */
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

        String key = INVENTORY_STATICS_IN_KEY_PREFIX + TenantPermissionContext.getTenantCode() + ":" + LocalDate.now();
        stringRedisTemplate.opsForValue().increment(key, inventoryInRequest.getMeters().doubleValue());
        stringRedisTemplate.expire(key, redisUtil.getSecondsToNextDay(), TimeUnit.SECONDS);
    }

    public Cloth selectClothByBarCode(String barCode) {
        return clothMapper.selectOne(new LambdaQueryWrapper<Cloth>()
                .eq(Cloth::getBarcode, barCode)
                .eq(Cloth::getTenantCode, TenantPermissionContext.getTenantCode()));
    }

    public List<ClothModelSpec> searchModelSpec(String keyword) {
        LambdaQueryWrapper<ClothModelSpec> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(keyword)) {
            queryWrapper.like(ClothModelSpec::getModelCode, keyword);
        }
        return clothModelSpecMapper.selectList(queryWrapper);
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

        Float todayIn = redisUtil.getHashValue(REDIS_TODAY_IN, TenantPermissionContext.getTenantCode(), Float.class);
        Float todayOut = redisUtil.getHashValue(REDIS_TODAY_OUT, TenantPermissionContext.getTenantCode(), Float.class);

        List<String> dateList = new ArrayList<>();
        List<Float> inList = new ArrayList<>();
        List<Float> outList = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {
            LocalDateTime date = now.minusDays(i);
            LocalDate currentDay = date.toLocalDate();
            String dateStr = date.format(formatter);
            dateList.add(dateStr);

            if (i == 0) {
                inList.add(todayIn);
                outList.add(todayOut);
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

    public void finishOutbound(String orderNo) {
        submitOutboundToPrint(orderNo);
    }

    public void submitOutboundToPrint(String orderNo) {
        String tenantCode = TenantPermissionContext.getTenantCode();

        LambdaUpdateWrapper<OutboundOrder> luw = new LambdaUpdateWrapper<>();
        luw.eq(OutboundOrder::getTenantCode, tenantCode)
                .eq(OutboundOrder::getOrderNo, orderNo)
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
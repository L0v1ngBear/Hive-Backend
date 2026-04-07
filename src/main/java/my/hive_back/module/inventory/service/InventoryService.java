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
import my.hive_back.module.inventory.model.entity.OutboundOrder;
import my.hive_back.module.inventory.model.entity.OutboundItem;
import my.hive_back.module.statics.inventory.mapper.InventoryTrendStaticsMapper;
import my.hive_back.module.inventory.model.vo.ClothInfoVO;
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

    @Value("${redis.key-prefix.trend.today_in}")
    private String REDIS_TODAY_IN;
    @Value("${redis.key-prefix.trend.today_out}")
    private String REDIS_TODAY_OUT;

    private static final String INVENTORY_STATICS_IN_KEY_PREFIX = "inventory:in:statics:";
    private static final String INVENTORY_STATICS_OUT_KEY_PREFIX = "inventory:out:statics:";
    private static final String CLOTH_OUT_LOCK_PREFIX = "lock:cloth:out:";


    /**
     * 统一入库入口
     */
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission(value = "inventory:in", message = "无权操作库存入库")
    public ClothInfoVO inCloth(@Valid InventoryInRequest inventoryInRequest) {
        InventoryInTypeEnum inTypeEnum = InventoryInTypeEnum.getCode(inventoryInRequest.getInType());
        String barcode;

        // 1. 生成条码逻辑
        switch (inTypeEnum) {
            case SCAN -> barcode = inventoryInRequest.getBarcode();
            case HAND, AUTO -> {
                barcode = barCodeUtil.createBarCode(TenantPermissionContext.getTenantCode());
                inventoryInRequest.setBarcode(barcode);
                inventoryHandIn(inventoryInRequest);
            }
            default -> throw new BusinessException("未知的入库类型");
        }

        // 2. 异步维护型号规格：规格库插入不影响主流程入库结果
        saveClothModelSpecAsync(inventoryInRequest.getModelCode(), inventoryInRequest.getSpec(), TenantPermissionContext.getTenantCode());

        ClothInfoVO clothInfoVO = new ClothInfoVO();
        BeanUtils.copyProperties(inventoryInRequest, clothInfoVO);
        return clothInfoVO;
    }

    /**
     * 出库逻辑
     */
    @RequirePermission(value = "inventory:out", message = "无权操作库存出库")
    @Transactional(rollbackFor = Exception.class)
    public ClothInfoVO outCloth(@Valid InventoryOutRequest request) {
        String barCode = request.getBarcode();
        String orderNo = request.getOrderNo();
        String customerName = request.getCustomerName();
        String tenantCode = TenantPermissionContext.getTenantCode();
        Long userId = TenantPermissionContext.getUserId();

        // 1. 防御性检查
        if (barCode == null) {
            throw new BusinessException("非法条码请求");
        }

        String lockKey = CLOTH_OUT_LOCK_PREFIX + tenantCode + ":" + barCode;

        // 2. 分布式锁：防止短时间内对同一条码的高并发冲击
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 5, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(locked)) {
            throw new BusinessException("该布卷正在处理中，请稍后");
        }

        try {
            // 3. 查询原布匹信息
            Cloth cloth = selectClothByBarCode(barCode);
            if (cloth == null) {
                throw new BusinessException("条码不存在");
            }

            Float metersToOut = request.getMeters();

            // 3.1 执行出库扣减 (保持你原有的优秀逻辑不变)
            if (metersToOut != null && metersToOut > 0) {
                LambdaUpdateWrapper<Cloth> luw = new LambdaUpdateWrapper<>();
                luw.eq(Cloth::getBarcode, barCode)
                        .eq(Cloth::getTenantCode, tenantCode)
                        .ge(Cloth::getRemainingMeters, metersToOut)
                        .setSql("remaining_meters = remaining_meters - " + metersToOut)
                        .set(Cloth::getOutTime, LocalDateTime.now())
                        .set(Cloth::getOutOperatorId, userId)
                        .setSql("status = CASE WHEN remaining_meters = " + metersToOut + " THEN " + InventoryOperateTypeEnum.OUT.getCode() +
                                " ELSE " + InventoryOperateTypeEnum.PART_OUT.getCode() + " END");

                int rows = clothMapper.update(luw);
                if (rows == 0) {
                    throw new BusinessException("出库失败：库存不足或状态异常");
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

            // ==================== 【新增：出库单据归集逻辑】 ====================
            // 因为在此处上方已经扣减成功，且处于 @Transactional 内，若下方报错，上面的库存扣减会安全回滚。

            // a. 查找当前租户下，该客户是否存在“待打印 (0)”的合并出库单
            LambdaQueryWrapper<OutboundOrder> orderQuery = new LambdaQueryWrapper<>();
            orderQuery.eq(OutboundOrder::getTenantCode, tenantCode)
                    .eq(OutboundOrder::getOrderNo, orderNo)
                    .eq(OutboundOrder::getPrintStatus, 0) // 0-待打印
                    .last("LIMIT 1"); // 保证并发下只查一条

            OutboundOrder order = outboundOrderMapper.selectOne(orderQuery);

            // b. 如果没有待打印单据，说明是今天扫的该客户的第一卷布，新建单据主表
            if (order == null) {
                order = new OutboundOrder();
                order.setTenantCode(tenantCode);
                order.setOrderNo(codeGeneratorUtil.generateOutboundOrderNo()); // 生成诸如 CK20260407001 的单号
                order.setCustomerName(customerName);
                order.setPrintStatus(0); // 置为待打印，等待 Vue PC端拉取
                order.setOperatorId(userId);
                order.setCreateTime(LocalDateTime.now());
                outboundOrderMapper.insert(order); // MyBatis-Plus 会自动回填 ID
            }

            // c. 无论主表是新查出的还是新建的，都将本次扫码作为“明细”挂载进去
            OutboundItem item = new OutboundItem();
            item.setTenantCode(tenantCode);
            item.setOrderId(order.getId());
            item.setBarcode(cloth.getBarcode());
            item.setModelCode(cloth.getModelCode());
            item.setSpec(cloth.getSpec());
            // 注意这里是本次出库的米数(metersToOut)，而不是布匹的剩余米数
            item.setMeters(metersToOut);

            // d. 计算价格（PC端打印出库单需要显示金额）
            // 建议：如果你有单独的型号表，请根据 cloth.getModelCode() 去查询设定的单价
            BigDecimal price = modelMapper.getPrice(tenantCode, cloth.getModelCode());
            item.setPrice(price);
            // 计算金额：单价 * 米数 (将 Float 转为 BigDecimal 计算避免精度丢失)
            item.setTotalAmount(price.multiply(BigDecimal.valueOf(metersToOut)));

            outboundItemMapper.insert(item);
            // ===================================================================

            // 4. 发送异步通知：记录流水与统计（不阻塞主事务提交）
            asyncLogAndStatics(barCode, tenantCode, userId, metersToOut, INVENTORY_STATICS_OUT_KEY_PREFIX);

            // 5. 出库成功后，组装完整的出库信息返回给前端打印（注意这里返回的仍是布匹操作后的余量信息）
            ClothInfoVO clothInfoVO = new ClothInfoVO();
            BeanUtils.copyProperties(cloth, clothInfoVO);
            clothInfoVO.setMeters(cloth.getRemainingMeters());

            return clothInfoVO;
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    /**
     * 异步记录流水与统计：大幅度提升接口响应速度
     */
    @Async
    public void asyncLogAndStatics(String barCode, String tenantCode, Long userId, Float meters, String staticsPrefix) {
        try {
            Cloth cloth = clothMapper.selectOne(new LambdaQueryWrapper<Cloth>().eq(Cloth::getBarcode, barCode).eq(Cloth::getTenantCode, tenantCode));
            if (cloth == null) return;

            // 记录流水
            InventoryRecord record = new InventoryRecord();
            record.setTenantCode(tenantCode);
            record.setClothId(cloth.getId());
            record.setOperatorId(userId);
            record.setOperateType(InventoryOperateTypeEnum.OUT.getCode());
            record.setOperateMeters(meters);
            record.setRemainingMeters(cloth.getRemainingMeters());
            inventoryRecordMapper.insert(record);

            // Redis 累加统计
            String key = staticsPrefix + tenantCode + ":" + LocalDate.now();
            stringRedisTemplate.opsForValue().increment(key, meters.doubleValue());
            stringRedisTemplate.expire(key, redisUtil.getSecondsToNextDay(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("异步记录出库统计失败, barcode: {}", barCode, e);
        }
    }

    /**
     * 异步保存规格：防止主入库流程因规格库小概率报错而回滚
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

        // 入库流水记录（暂未异步，入库频率通常低于出库/查询）
        InventoryRecord record = new InventoryRecord();
        record.setClothId(cloth.getId());
        record.setModelCode(inventoryInRequest.getModelCode());
        record.setTenantCode(TenantPermissionContext.getTenantCode());
        record.setOperatorId(TenantPermissionContext.getUserId());
        record.setOperateType(InventoryOperateTypeEnum.IN.getCode());
        record.setOperateMeters(inventoryInRequest.getMeters());
        record.setRemainingMeters(inventoryInRequest.getMeters());
        inventoryRecordMapper.insert(record);

        // Redis 统计原子累加
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
        if (!StringUtils.isBlank(keyword)) {
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

        // 1. 先查【前6天】数据（从DB）
        LocalDateTime dbEndDate = now.minusDays(1);  // 截止到昨天
        LocalDateTime dbStartDate = now.minusDays(6); // 往前6天
        LambdaQueryWrapper<InventoryTrendStatics> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(InventoryTrendStatics::getStatDate, dbStartDate, dbEndDate);
        wrapper.orderByAsc(InventoryTrendStatics::getStatDate);
        List<InventoryTrendStatics> dbList = staticsMapper.selectList(wrapper);

        // 2. 查【今天】实时数据（从Redis）
        Float todayIn = redisUtil.getHashValue(REDIS_TODAY_IN, TenantPermissionContext.getTenantCode(), Float.class);
        Float todayOut = redisUtil.getHashValue(REDIS_TODAY_OUT, TenantPermissionContext.getTenantCode(), Float.class);

        // 3. 组装 7 天数据（自动补0）
        List<String> dateList = new ArrayList<>();
        List<Float> inList = new ArrayList<>();
        List<Float> outList = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {
            LocalDateTime date = now.minusDays(i);
            LocalDate currentDay = date.toLocalDate();
            String dateStr = date.format(formatter);
            dateList.add(dateStr);

            if (i == 0) {
                // ======================
                // 今天 → 从 Redis 取
                // ======================
                inList.add(todayIn);
                outList.add(todayOut);
            } else {
                // ======================
                // 前6天 → 从 DB 取
                // ======================
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
        String tenantCode = TenantPermissionContext.getTenantCode();

        // 将该客户名下所有“扫码中(0)”的单据改为“待打印(1)”
        LambdaUpdateWrapper<OutboundOrder> luw = new LambdaUpdateWrapper<>();
        luw.eq(OutboundOrder::getTenantCode, tenantCode)
                .eq(OutboundOrder::getOrderNo, orderNo)
                .eq(OutboundOrder::getPrintStatus, 0)
                .set(OutboundOrder::getPrintStatus, 1)
                .set(OutboundOrder::getUpdateTime, LocalDateTime.now());

        int rows = outboundOrderMapper.update(null, luw);
        if (rows == 0) {
            throw new BusinessException("未发现可结单的记录");
        }
    }
}
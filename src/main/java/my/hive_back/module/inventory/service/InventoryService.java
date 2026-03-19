package my.hive_back.module.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.Synchronized;
import my.hive_back.common.annotation.RequirePermission;
import my.hive_back.common.context.TenantPermissionContext;
import my.hive_back.common.exception.BusinessException;
import my.hive_back.common.interceptor.TenantInterceptor;
import my.hive_back.common.utils.BarCodeUtil;
import my.hive_back.common.utils.RedisUtil;
import my.hive_back.common.utils.TimeUtil;
import my.hive_back.module.inventory.InventoryInTypeEnum;
import my.hive_back.module.inventory.InventoryOperateTypeEnum;
import my.hive_back.module.inventory.mapper.ClothMapper;
import my.hive_back.module.inventory.mapper.InventoryRecordMapper;
import my.hive_back.module.inventory.model.dto.InventoryInRequest;
import my.hive_back.module.inventory.model.dto.InventoryOutRequest;
import my.hive_back.module.inventory.model.entity.Cloth;
import my.hive_back.module.inventory.model.entity.InventoryRecord;
import my.hive_back.module.inventory.model.entity.InventoryStatics;
import my.hive_back.module.inventory.mapper.InventoryStaticsMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;


@Service
public class InventoryService {

    @Resource
    private InventoryStaticsMapper staticsMapper;

    @Resource
    private InventoryRecordMapper inventoryRecordMapper;

    @Resource
    private BarCodeUtil barCodeUtil;

    @Resource
    private ClothMapper clothMapper;

    private static final String INVENTORY_STATICS_IN_KEY_PREFIX = "inventory:in:statics";
    private static final String INVENTORY_STATICS_OUT_KEY_PREFIX = "inventory:out:statics";

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedisUtil redisUtil;

    public InventoryStatics selectInventoryStatics() {

        // 构造当前日期的时间范围
        LocalDate today = LocalDate.now();
        LocalDateTime startTime = today.atStartOfDay(); // 当天00:00:00
        LocalDateTime endTime = today.plusDays(1).atStartOfDay().minusNanos(1); // 当天23:59:59.999999999

        // 数据来源于定时任务的统计
        // TODO 统计数据
        LambdaQueryWrapper<InventoryStatics> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(InventoryStatics::getCreateTime, startTime, endTime);

        return staticsMapper.selectOne(wrapper);
    }

    /**
     * 统一入库入口（兼容扫码/手动/自动入库）
     *
     * @param inventoryInRequest 入库请求
     */
    @Synchronized
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission(value = "inventory:in", message = "无权操作库存入库")
    public String inCloth(@Valid InventoryInRequest inventoryInRequest) {

        InventoryInTypeEnum inTypeEnum = InventoryInTypeEnum.valueOf(inventoryInRequest.getInType());
        switch (inTypeEnum) {
            case SCAN:
                InventoryScanIn(inventoryInRequest.getBarcode());
                break;
            case HAND:
                //手动入库补充条码信息入库
                CompleteBarcode(inventoryInRequest);
                return InventoryHandIn(inventoryInRequest);
//                break;
            case AUTO:
                // 自动入库补充条码信息入库
                CompleteBarcode(inventoryInRequest);
                InventoryAutoIn(inventoryInRequest);
                break;
            default:
                throw new IllegalArgumentException("未知的入库类型");
        }

        //TODO 统一放入redis库存统计
        return null;
    }

    private void CompleteBarcode(InventoryInRequest request) {
        String barCode = barCodeUtil.createBarCode(TenantPermissionContext.getTenantCode());
        request.setBarcode(barCode);
    }


    //TODO 自动入库
    private void InventoryAutoIn(@Valid InventoryInRequest inventoryInRequest) {

    }


    //TODO 扫码入库
    private void InventoryScanIn(String barcode) {

    }

    private String InventoryHandIn(InventoryInRequest inventoryInRequest) {

        Cloth cloth = new Cloth();
        BeanUtils.copyProperties(inventoryInRequest, cloth);
        cloth.setInOperatorId(TenantPermissionContext.getUserId());
        cloth.setInTime(TimeUtil.now());
        cloth.setTotalMeters(inventoryInRequest.getMeters());
        cloth.setRemainingMeters(inventoryInRequest.getMeters());
        cloth.setStatus(InventoryOperateTypeEnum.IN.getCode());

        clothMapper.insert(cloth);

        //TODO 打印条形码
//        barCodeUtil.createBarCodeImage(cloth.getBarcode(), 200, 100);
        String barCode = cloth.getBarcode();

        // 记录入库操作
        InventoryRecord record = new InventoryRecord();
        record.setTenantCode(TenantPermissionContext.getTenantCode());
        record.setOperatorId(TenantPermissionContext.getUserId());
        record.setOperateType(InventoryOperateTypeEnum.IN.getCode());
        record.setOperateMeters(inventoryInRequest.getMeters());
        record.setTotalMeters(inventoryInRequest.getMeters());
        inventoryRecordMapper.insert(record);


        //TODO 需要持久化进数据库，选择rabbitmq,异步匀速持久化

        // 更新库存统计,先存入redis
        String inKey = INVENTORY_STATICS_IN_KEY_PREFIX + ":" + TenantPermissionContext.getTenantCode();

        String meters = inventoryInRequest.getMeters().toString();
        long secondsToNextDay = redisUtil.getSecondsToNextDay();


        stringRedisTemplate.opsForSet().add(inKey, meters);
        stringRedisTemplate.expire(inKey, secondsToNextDay, TimeUnit.SECONDS);
        return barCode;
    }

    @RequirePermission(value = "inventory:out", message = "无权操作库存出库")
    @Transactional(rollbackFor = Exception.class)
    public void outCloth(@Valid InventoryOutRequest inventoryOutRequest) {

        // 校验 cloth
        LambdaQueryWrapper<Cloth> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(Cloth::getBarcode, inventoryOutRequest.getBarCode());

        Cloth cloth = clothMapper.selectOne(wrapper);

        if (cloth == null) {
            throw new IllegalArgumentException("该布不存在");
        }

        Float meters = inventoryOutRequest.getMeters();

        String barCode = inventoryOutRequest.getBarCode();

        Long clothId = cloth.getId();

        LambdaUpdateWrapper<Cloth> updateWrapper = new LambdaUpdateWrapper<>();

        if (meters == null || meters <= 0) {
            updateWrapper.set(Cloth::getRemainingMeters, 0);
            updateWrapper.set(Cloth::getOutTime, TimeUtil.now());
            updateWrapper.set(Cloth::getStatus, InventoryOperateTypeEnum.OUT.getCode());
        } else {
            float remainMeters = cloth.getRemainingMeters() - meters;
            if (remainMeters < 0) {
                throw new BusinessException("该布仅剩" + cloth.getRemainingMeters() + "米，无法出库" + meters + "米");
            }
            updateWrapper.set(Cloth::getRemainingMeters, remainMeters);
            updateWrapper.set(Cloth::getOutTime, TimeUtil.now());
            updateWrapper.set(Cloth::getStatus, InventoryOperateTypeEnum.PART_OUT.getCode());

            //TODO 重新打印条形码

        }
        updateWrapper.set(Cloth::getOutOperatorId, TenantPermissionContext.getUserId());
        updateWrapper.eq(Cloth::getBarcode, barCode);
        clothMapper.update(updateWrapper);

        // 记录出库操作
        InventoryRecord record = new InventoryRecord();
        record.setTenantCode(TenantPermissionContext.getTenantCode());
        record.setClothId(clothId);
        record.setOperatorId(TenantPermissionContext.getUserId());
        record.setOperateType(InventoryOperateTypeEnum.OUT.getCode());
        record.setOperateMeters(meters);
        inventoryRecordMapper.insert(record);

        String outKey = INVENTORY_STATICS_OUT_KEY_PREFIX + ":" + TenantPermissionContext.getTenantCode();

        String opMeters = inventoryOutRequest.getMeters() != null ? inventoryOutRequest.getMeters().toString() : "0";

        long secondsToNextDay = redisUtil.getSecondsToNextDay();
        stringRedisTemplate.opsForSet().add(outKey, opMeters);
        stringRedisTemplate.expire(outKey, secondsToNextDay, TimeUnit.SECONDS);
    }

    @RequirePermission(value = "inventory:search", message = "无权库存查询")
    public Cloth selectClothByBarCode(String barCode) {
        LambdaQueryWrapper<Cloth> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(Cloth::getBarcode, barCode);

        return clothMapper.selectOne(wrapper);
    }
}

package my.hive_back.module.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.Synchronized;
import my.hive_back.common.context.TenantPermissionContext;
import my.hive_back.common.interceptor.TenantInterceptor;
import my.hive_back.common.utils.BarCodeUtil;
import my.hive_back.module.inventory.InventoryInTypeEnum;
import my.hive_back.module.inventory.InventoryOperateTypeEnum;
import my.hive_back.module.inventory.mapper.ClothMapper;
import my.hive_back.module.inventory.mapper.InventoryRecordMapper;
import my.hive_back.module.inventory.model.dto.InventoryInRequest;
import my.hive_back.module.inventory.model.entity.Cloth;
import my.hive_back.module.inventory.model.entity.InventoryRecord;
import my.hive_back.module.inventory.model.entity.InventoryStatics;
import my.hive_back.module.inventory.mapper.InventoryStaticsMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;


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
    public void inCloth(@Valid InventoryInRequest inventoryInRequest) {

        InventoryInTypeEnum inTypeEnum = InventoryInTypeEnum.valueOf(inventoryInRequest.getInType());
        switch (inTypeEnum) {
            case SCAN:
                InventoryScanIn(inventoryInRequest.getBarcode());
                break;
            case HAND:
                //手动入库补充条码信息入库
                CompleteBarcode(inventoryInRequest);
                InventoryHandIn(inventoryInRequest);
                break;
            case AUTO:
                // 自动入库补充条码信息入库
                CompleteBarcode(inventoryInRequest);
                InventoryAutoIn(inventoryInRequest);
                break;
            default:
                throw new IllegalArgumentException("未知的入库类型");
        }

        //TODO 统一放入redis库存统计
    }

    private void CompleteBarcode(InventoryInRequest request) {
        String barCode = barCodeUtil.createBarCode(TenantPermissionContext.getTenantCode());
        request.setBarcode(barCode);
    }

    private void InventoryAutoIn(@Valid InventoryInRequest inventoryInRequest) {
    }



    private void InventoryScanIn(String barcode) {

    }

    private void InventoryHandIn(InventoryInRequest inventoryInRequest) {

        Cloth cloth = new Cloth();
        BeanUtils.copyProperties(inventoryInRequest, cloth);
        cloth.setInOperatorId(TenantPermissionContext.getUserId());
        cloth.setInTime(LocalDateTime.now());
        cloth.setTotalMeters(inventoryInRequest.getMeters());
        cloth.setRemainingMeters(inventoryInRequest.getMeters());
        cloth.setStatus(InventoryOperateTypeEnum.IN.getCode());

        clothMapper.insert(cloth);

        //TODO 打印条形码
        barCodeUtil.createBarCodeImage(cloth.getBarcode(), 200, 100);
    }
}

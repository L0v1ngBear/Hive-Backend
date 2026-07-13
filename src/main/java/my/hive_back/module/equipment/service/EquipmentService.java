package my.hive_back.module.equipment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive_back.module.equipment.mapper.EquipmentDeviceMapper;
import my.hive_back.module.equipment.mapper.EquipmentInspectionRecordMapper;
import my.hive_back.module.equipment.model.dto.EquipmentInspectionSubmitRequest;
import my.hive_back.module.equipment.model.dto.EquipmentPageRequest;
import my.hive_back.module.equipment.model.dto.EquipmentRecordPageRequest;
import my.hive_back.module.equipment.model.entity.EquipmentDevice;
import my.hive_back.module.equipment.model.entity.EquipmentInspectionRecord;
import my.hive_back.module.equipment.model.vo.EquipmentDeviceVO;
import my.hive_back.module.equipment.model.vo.EquipmentInspectionRecordVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class EquipmentService {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;
    private static final String STATUS_ENABLED = "enabled";
    private static final String RESULT_NORMAL = "normal";
    private static final String RESULT_ABNORMAL = "abnormal";

    @Resource
    private EquipmentDeviceMapper equipmentDeviceMapper;

    @Resource
    private EquipmentInspectionRecordMapper inspectionRecordMapper;

    public Page<EquipmentDeviceVO> page(EquipmentPageRequest request) {
        EquipmentPageRequest safeRequest = request == null ? new EquipmentPageRequest() : request;
        LambdaQueryWrapper<EquipmentDevice> wrapper = new LambdaQueryWrapper<EquipmentDevice>()
                .eq(EquipmentDevice::getTenantCode, TenantPermissionContext.getTenantCode())
                .eq(EquipmentDevice::getStatus, STATUS_ENABLED);
        String keyword = cleanText(safeRequest.getKeyword());
        if (keyword != null) {
            wrapper.and(w -> w
                    .like(EquipmentDevice::getEquipmentCode, keyword)
                    .or().like(EquipmentDevice::getEquipmentName, keyword)
                    .or().like(EquipmentDevice::getEquipmentType, keyword)
                    .or().like(EquipmentDevice::getLocation, keyword)
                    .or().like(EquipmentDevice::getResponsiblePerson, keyword));
        }
        wrapper.orderByDesc(EquipmentDevice::getUpdateTime).orderByDesc(EquipmentDevice::getId);
        Page<EquipmentDevice> entityPage = equipmentDeviceMapper.selectPage(
                new Page<>(safePageNum(safeRequest.getPageNum()), safePageSize(safeRequest.getPageSize())),
                wrapper);
        Page<EquipmentDeviceVO> result = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        result.setPages(entityPage.getPages());
        result.setRecords(entityPage.getRecords().stream().map(this::toDeviceVO).toList());
        return result;
    }

    public EquipmentDeviceVO scanTarget(String equipmentCode) {
        return toDeviceVO(findDeviceByCode(equipmentCode));
    }

    public Page<EquipmentInspectionRecordVO> recordPage(EquipmentRecordPageRequest request) {
        EquipmentRecordPageRequest safeRequest = request == null ? new EquipmentRecordPageRequest() : request;
        LambdaQueryWrapper<EquipmentInspectionRecord> wrapper = new LambdaQueryWrapper<EquipmentInspectionRecord>()
                .eq(EquipmentInspectionRecord::getTenantCode, TenantPermissionContext.getTenantCode());

        if (safeRequest.getEquipmentId() != null && safeRequest.getEquipmentId() > 0) {
            wrapper.eq(EquipmentInspectionRecord::getEquipmentId, safeRequest.getEquipmentId());
        }
        String equipmentCode = cleanText(safeRequest.getEquipmentCode());
        if (equipmentCode != null) {
            wrapper.eq(EquipmentInspectionRecord::getEquipmentCode, equipmentCode);
        }
        String result = normalizeOptionalResult(safeRequest.getResult());
        if (result != null) {
            wrapper.eq(EquipmentInspectionRecord::getInspectionResult, result);
        }
        wrapper.orderByDesc(EquipmentInspectionRecord::getInspectionTime).orderByDesc(EquipmentInspectionRecord::getId);

        Page<EquipmentInspectionRecord> entityPage = inspectionRecordMapper.selectPage(
                new Page<>(safePageNum(safeRequest.getPageNum()), safePageSize(safeRequest.getPageSize())),
                wrapper);
        Page<EquipmentInspectionRecordVO> resultPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        resultPage.setPages(entityPage.getPages());
        resultPage.setRecords(entityPage.getRecords().stream().map(this::toRecordVO).toList());
        return resultPage;
    }

    @Transactional(rollbackFor = Exception.class)
    public EquipmentInspectionRecordVO submitInspection(EquipmentInspectionSubmitRequest request) {
        EquipmentDevice device = findDeviceByCode(request.getEquipmentCode());
        String result = normalizeResult(request.getInspectionResult());
        String abnormalDesc = cleanText(request.getAbnormalDesc());
        if (RESULT_ABNORMAL.equals(result) && abnormalDesc == null) {
            throw new BusinessException("异常巡检必须填写异常说明");
        }
        LocalDateTime inspectionTime = request.getInspectionTime() == null ? LocalDateTime.now() : request.getInspectionTime();
        EquipmentInspectionRecord record = new EquipmentInspectionRecord();
        record.setTenantCode(device.getTenantCode());
        record.setEquipmentId(device.getId());
        record.setEquipmentCode(device.getEquipmentCode());
        record.setEquipmentName(device.getEquipmentName());
        record.setInspectionResult(result);
        record.setAbnormalDesc(abnormalDesc);
        record.setPhotoUrl(cleanText(request.getPhotoUrl()));
        record.setRemark(cleanText(request.getRemark()));
        record.setInspectorUserId(TenantPermissionContext.getUserId());
        record.setInspectorName(TenantPermissionContext.getUserId() == null ? "系统用户" : "用户" + TenantPermissionContext.getUserId());
        record.setInspectionTime(inspectionTime);
        inspectionRecordMapper.insert(record);

        equipmentDeviceMapper.update(null, new LambdaUpdateWrapper<EquipmentDevice>()
                .eq(EquipmentDevice::getTenantCode, device.getTenantCode())
                .eq(EquipmentDevice::getId, device.getId())
                .set(EquipmentDevice::getLastInspectionTime, inspectionTime)
                .set(EquipmentDevice::getUpdateTime, LocalDateTime.now()));
        return toRecordVO(record);
    }

    private EquipmentDevice findDeviceByCode(String equipmentCode) {
        String safeCode = cleanText(equipmentCode);
        if (safeCode == null) {
            throw new BusinessException("设备编码不能为空");
        }
        EquipmentDevice device = equipmentDeviceMapper.selectOne(new LambdaQueryWrapper<EquipmentDevice>()
                .eq(EquipmentDevice::getTenantCode, TenantPermissionContext.getTenantCode())
                .eq(EquipmentDevice::getEquipmentCode, safeCode)
                .eq(EquipmentDevice::getStatus, STATUS_ENABLED)
                .last("LIMIT 1"));
        if (device == null) {
            throw new BusinessException("设备不存在或已停用");
        }
        return device;
    }

    private EquipmentDeviceVO toDeviceVO(EquipmentDevice device) {
        EquipmentDeviceVO vo = new EquipmentDeviceVO();
        BeanUtils.copyProperties(device, vo);
        vo.setInspectionQrPayload(buildInspectionQrPayload(device.getEquipmentCode()));
        return vo;
    }

    private EquipmentInspectionRecordVO toRecordVO(EquipmentInspectionRecord record) {
        EquipmentInspectionRecordVO vo = new EquipmentInspectionRecordVO();
        BeanUtils.copyProperties(record, vo);
        return vo;
    }

    private String normalizeResult(String result) {
        String safe = cleanText(result);
        if (RESULT_NORMAL.equals(safe) || RESULT_ABNORMAL.equals(safe)) {
            return safe;
        }
        throw new BusinessException("巡检结果不合法");
    }

    private String normalizeOptionalResult(String result) {
        String safe = cleanText(result);
        if (safe == null) {
            return null;
        }
        if (RESULT_NORMAL.equals(safe) || RESULT_ABNORMAL.equals(safe)) {
            return safe;
        }
        throw new BusinessException("巡检结果不合法");
    }

    private String cleanText(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    private String buildInspectionQrPayload(String equipmentCode) {
        String safeCode = equipmentCode == null ? "" : equipmentCode.trim();
        return safeCode.isEmpty() ? "" : "HIVE_EQUIPMENT:" + safeCode;
    }

    private int safePageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? DEFAULT_PAGE_NUM : pageNum;
    }

    private int safePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}

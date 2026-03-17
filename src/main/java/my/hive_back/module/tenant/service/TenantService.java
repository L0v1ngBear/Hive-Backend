package my.hive_back.module.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import my.hive_back.common.context.TenantPermissionContext;
import my.hive_back.common.exception.BusinessException;
import my.hive_back.module.tenant.mapper.TenantAttendanceInfoMapper;
import my.hive_back.module.tenant.mapper.TenantMapper;
import my.hive_back.module.tenant.model.dto.TenantInfoPageRequest;
import my.hive_back.module.tenant.model.dto.TenantLocationAddRequest;
import my.hive_back.module.tenant.model.entity.Tenant;
import my.hive_back.module.tenant.model.entity.TenantAttendanceRule;
import org.springframework.stereotype.Service;

@Service
public class TenantService {

    @Resource
    private TenantMapper tenantMapper;

    @Resource
    private TenantAttendanceInfoMapper tenantLocationMapper;

    public Page<Tenant> pageSearchTenant(TenantInfoPageRequest searchDTO) {
        Integer status = searchDTO.getStatus();
        Integer isDeleted = searchDTO.getIsDeleted();

        Page<Tenant> page = new Page<>(searchDTO.getPageNum(), searchDTO.getPageSize());
        LambdaQueryWrapper<Tenant> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Tenant::getDeleted, isDeleted);
        queryWrapper.eq(Tenant::getStatus, status);
        queryWrapper.like(Tenant::getTenantName, searchDTO.getTenantName());
        queryWrapper.orderByDesc(Tenant::getUpdateTime);

        return tenantMapper.selectPage(page, queryWrapper);
    }

    public TenantAttendanceRule addTenantLocation(TenantLocationAddRequest tenantLocationAddRequest) {
        TenantAttendanceRule tenantLocation = new TenantAttendanceRule();

        //TODO 对接高德api，获取经纬度

        // 0表示未校准
        tenantLocation.setStatus(0);
        return tenantLocation;
    }

    public TenantAttendanceRule getTenantLocation() {
        String tenantCode = TenantPermissionContext.getTenantCode();
        if (tenantCode == null) {
            throw new BusinessException("没有权限或租户不存在");
        }
        LambdaQueryWrapper<TenantAttendanceRule> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TenantAttendanceRule::getTenantCode, tenantCode);
        TenantAttendanceRule tenantLocation = tenantLocationMapper.selectOne(queryWrapper);
        if (tenantLocation == null) {
            throw new BusinessException("租户不存在");
        }
        return tenantLocation;
    }
}

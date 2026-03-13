package my.hive_back.module.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import my.hive_back.module.tenant.mapper.TenantMapper;
import my.hive_back.module.tenant.model.dto.TenantInfoPageRequest;
import my.hive_back.module.tenant.model.entity.Tenant;
import my.hive_back.module.tenant.service.impl.TenantServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class TenantService implements TenantServiceImpl {

    @Resource
    private TenantMapper tenantMapper;

    @Override
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
}

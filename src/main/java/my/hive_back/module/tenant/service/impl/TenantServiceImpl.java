package my.hive_back.module.tenant.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import my.hive_back.module.tenant.model.dto.TenantInfoPageRequest;
import my.hive_back.module.tenant.model.entity.Tenant;
import org.springframework.stereotype.Service;

@Service
public interface TenantServiceImpl{

    Page<Tenant> pageSearchTenant(TenantInfoPageRequest request);
}

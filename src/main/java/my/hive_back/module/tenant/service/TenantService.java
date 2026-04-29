package my.hive_back.module.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive_back.module.tenant.mapper.TenantAttendanceRuleMapper;
import my.hive_back.module.tenant.mapper.TenantMapper;
import my.hive_back.module.tenant.model.dto.TenantLocationAddRequest;
import my.hive_back.module.tenant.model.entity.TenantAttendanceRule;
import org.springframework.stereotype.Service;
/**
 * TenantService 属于小程序后端租户模块，实现核心业务编排与规则逻辑。
 */
@Service
public class TenantService {

    @Resource
    private TenantMapper tenantMapper;

    @Resource
    private TenantAttendanceRuleMapper tenantLocationMapper;

    public TenantAttendanceRule addTenantLocation(TenantLocationAddRequest tenantLocationAddRequest) {
        TenantAttendanceRule tenantLocation = new TenantAttendanceRule();

        // 预留地图服务扩展点：后续接入高德 API 后，可由地址解析经纬度。

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

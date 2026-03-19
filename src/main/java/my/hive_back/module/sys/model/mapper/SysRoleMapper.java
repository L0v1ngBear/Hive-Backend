package my.hive_back.module.sys.model.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import my.hive_back.module.sys.model.entity.SysRole;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysRoleMapper extends BaseMapper<SysRole> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT perm_code FROM sys_permission WHERE tenant_code = #{tenantCode} AND role_code IN (#{roleCodes})")
    List<String> selectPermCodesByTenantAndRoleCodes(String tenantCode, List<String> roleCodes);
}

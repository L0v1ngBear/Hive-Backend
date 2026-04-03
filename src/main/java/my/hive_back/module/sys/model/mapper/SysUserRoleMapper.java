package my.hive_back.module.sys.model.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import my.hive_back.module.sys.model.entity.SysUserRole;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    @InterceptorIgnore(tenantLine = "true")
    List<String> selectPermCodesByUserIdAndTenantCode(Long userId, String tenantCode);
}

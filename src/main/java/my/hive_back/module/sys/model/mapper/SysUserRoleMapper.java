package my.hive_back.module.sys.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import my.hive_back.module.sys.model.entity.SysUserRole;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    @Select("SELECT role_code FROM sys_user_role WHERE user_id = #{userId} AND tenant_code = #{tenantCode}")
    List<String> selectRoleCodesByUserAndTenant(Long userId, String tenantCode);

}

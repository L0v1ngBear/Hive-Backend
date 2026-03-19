package my.hive_back.module.sys.model.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import my.hive_back.module.sys.model.entity.SysRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysRoleMapper extends BaseMapper<SysRole> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>" +
            "SELECT perm_code FROM sys_permission WHERE tenant_code = #{tenantCode} AND role_code IN " +
            "<foreach item='item' collection='roleCodes' open='(' separator=',' close=')'>" +
            "#{item}" +
            "</foreach>" +
            "</script>")
    List<String> selectPermCodesByTenantAndRoleCodes(@Param("tenantCode") String tenantCode, @Param("roleCodes") List<String> roleCodes);
}

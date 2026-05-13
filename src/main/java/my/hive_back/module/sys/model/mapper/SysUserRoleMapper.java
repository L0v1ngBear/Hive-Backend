package my.hive_back.module.sys.model.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import my.hive_back.module.sys.model.entity.SysUserRole;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * SysUserRoleMapper 属于小程序后端系统模块，是数据访问类，负责与数据库交互。
 */
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    @InterceptorIgnore(tenantLine = "true")
    List<String> selectPermCodesByUserIdAndTenantCode(@Param("userId") Long userId,
                                                       @Param("tenantCode") String tenantCode);

    @Select("""
            SELECT COUNT(1)
            FROM sys_user_role ur
            INNER JOIN sys_role r ON r.id = ur.role_id
                AND r.tenant_code = ur.tenant_code
                AND IFNULL(r.is_deleted, 0) = 0
            WHERE ur.user_id = #{userId}
              AND ur.tenant_code = #{tenantCode}
              AND IFNULL(ur.is_deleted, 0) = 0
            """)
    long countActiveRolesByUserIdAndTenantCode(@Param("userId") Long userId,
                                               @Param("tenantCode") String tenantCode);

    @Insert("""
            INSERT INTO sys_user_role (user_id, tenant_code, role_id, create_time, is_deleted)
            SELECT #{userId}, #{tenantCode}, #{roleId}, NOW(), 0
            WHERE NOT EXISTS (
                SELECT 1
                FROM sys_user_role
                WHERE user_id = #{userId}
                  AND tenant_code = #{tenantCode}
                  AND role_id = #{roleId}
                  AND IFNULL(is_deleted, 0) = 0
            )
            """)
    int insertIfAbsent(@Param("userId") Long userId,
                       @Param("tenantCode") String tenantCode,
                       @Param("roleId") Long roleId);
}

package my.hive_back.module.sys.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import my.hive_back.module.sys.model.entity.SysRolePermission;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * SysRolePermissionMapper 属于小程序后端系统模块，是角色权限关联数据访问类。
 */
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {

    @Insert("""
            INSERT INTO sys_role_permission (role_id, permission_id, create_time, is_deleted)
            VALUES (#{roleId}, #{permissionId}, NOW(), 0)
            ON DUPLICATE KEY UPDATE is_deleted = 0
            """)
    int insertIfAbsent(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);
}

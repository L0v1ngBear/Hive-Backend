package my.hive_back.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import my.hive_back.module.approval.model.vo.ApprovalAuditorOptionVO;
import my.hive_back.module.user.model.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * UserMapper 属于小程序后端用户模块，是数据访问类，负责与数据库交互。
 */
public interface UserMapper extends BaseMapper<User> {

    @Select({
            "SELECT DISTINCT u.id ",
            "FROM user u ",
            "INNER JOIN sys_user_role ur ",
            "  ON ur.user_id = u.id AND ur.tenant_code = u.tenant_code AND IFNULL(ur.is_deleted, 0) = 0 ",
            "INNER JOIN sys_role r ",
            "  ON r.id = ur.role_id AND r.tenant_code = u.tenant_code AND IFNULL(r.is_deleted, 0) = 0 ",
            "INNER JOIN sys_role_permission rp ",
            "  ON rp.role_id = r.id AND IFNULL(rp.is_deleted, 0) = 0 ",
            "INNER JOIN sys_permission p ",
            "  ON p.id = rp.permission_id AND IFNULL(p.is_deleted, 0) = 0 ",
            "WHERE u.tenant_code = #{tenantCode} ",
            "AND IFNULL(u.status, 1) <> 0 ",
            "AND (p.perm_code = #{permissionCode} ",
            "     OR p.perm_code = CONCAT(SUBSTRING_INDEX(#{permissionCode}, ':', 1), ':*') ",
            "     OR p.perm_code = CONCAT(SUBSTRING_INDEX(#{permissionCode}, ':', 2), ':*') ",
            "     OR p.perm_code IN ('*', '*:*')) ",
            "ORDER BY COALESCE(u.role_level, 0) DESC, u.id ASC"
    })
    List<Long> selectActiveApproverIdsByPermission(@Param("tenantCode") String tenantCode,
                                                   @Param("permissionCode") String permissionCode);

    @Select({
            "<script>",
            "SELECT DISTINCT u.id, u.name, u.department_name AS departmentName, u.position AS positionName ",
            "FROM user u ",
            "INNER JOIN sys_user_role ur ",
            "  ON ur.user_id = u.id AND ur.tenant_code = u.tenant_code AND IFNULL(ur.is_deleted, 0) = 0 ",
            "INNER JOIN sys_role r ",
            "  ON r.id = ur.role_id AND r.tenant_code = u.tenant_code AND IFNULL(r.is_deleted, 0) = 0 ",
            "INNER JOIN sys_role_permission rp ",
            "  ON rp.role_id = r.id AND IFNULL(rp.is_deleted, 0) = 0 ",
            "INNER JOIN sys_permission p ",
            "  ON p.id = rp.permission_id AND IFNULL(p.is_deleted, 0) = 0 ",
            "WHERE u.tenant_code = #{tenantCode} ",
            "AND IFNULL(u.status, 1) &lt;&gt; 0 ",
            "AND (p.perm_code = #{permissionCode} ",
            "     OR p.perm_code = CONCAT(SUBSTRING_INDEX(#{permissionCode}, ':', 1), ':*') ",
            "     OR p.perm_code = CONCAT(SUBSTRING_INDEX(#{permissionCode}, ':', 2), ':*') ",
            "     OR p.perm_code IN ('*', '*:*')) ",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (u.name LIKE CONCAT('%', #{keyword}, '%') ",
            "     OR u.department_name LIKE CONCAT('%', #{keyword}, '%') ",
            "     OR u.position LIKE CONCAT('%', #{keyword}, '%')) ",
            "</if>",
            "ORDER BY COALESCE(u.role_level, 0) DESC, u.id ASC ",
            "LIMIT #{limit}",
            "</script>"
    })
    List<ApprovalAuditorOptionVO> selectActiveApproverOptionsByPermission(@Param("tenantCode") String tenantCode,
                                                                          @Param("permissionCode") String permissionCode,
                                                                          @Param("keyword") String keyword,
                                                                          @Param("limit") Integer limit);
}

package my.hive_back.module.sys.model.entity;


import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("sys_role_permission")
@Data
public class SysRolePermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 角色ID（关联 SysRole）
     */
    private Long roleId;

    /**
     * 权限ID（关联 SysPermission）
     */
    private Long permissionId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    private Integer isDeleted;
}
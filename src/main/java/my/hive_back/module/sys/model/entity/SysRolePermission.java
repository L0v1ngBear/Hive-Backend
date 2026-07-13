package my.hive_back.module.sys.model.entity;


import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
/**
 * SysRolePermission 属于小程序后端系统模块，定义持久化实体结构，用于表字段映射。
 */
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

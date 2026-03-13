package my.hive_back.module.sys.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("sys_user_role")
@Data
public class SysUserRole {

    /**
     * 关联记录主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 租户编码（双重隔离：确保用户仅关联当前租户的角色）
     */
    private String tenantCode;

    /**
     * 角色ID（关联SysRole的id）
     */
    private Long roleId;

    /**
     * 创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 逻辑删除标识
     */
    @TableLogic
    private Integer isDeleted;

}

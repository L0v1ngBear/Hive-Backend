package my.hive_back.module.sys.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@TableName("sys_role")
@Data
public class SysRole {
    /**
     * 角色主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户编码（关联租户，隔离核心）
     */
    private String tenantCode;

    /**
     * 角色编码（租户内唯一）
     * 示例：TENANT_ADMIN（租户管理员）、TENANT_OPERATOR（租户操作员）
     */
    private String roleCode;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 是否系统内置角色：1-是 0-否
     * 系统内置角色不允许删除/修改
     */
    private Integer isSystem;

    /**
     * 创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标识
     */
    @TableLogic
    private Integer isDeleted;

    // --------------------- 非数据库字段 ---------------------
    /**
     * 角色对应的权限编码列表（查询时封装，不入库）
     */
    @TableField(exist = false)
    private List<String> permCodes;

}

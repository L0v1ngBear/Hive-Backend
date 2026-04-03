package my.hive_back.module.sys.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("sys_permission")
@Data
public class SysPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 父级权限ID（用于前端构建菜单树，顶级菜单为0）
     */
    private Long parentId;

    /**
     * 权限名称 (示例：用户管理、订单查看)
     */
    private String permName;

    /**
     * 权限编码（全局唯一核心标识）
     * 格式：资源:操作，示例：user:add、order:view
     */
    private String permCode;

    /**
     * 权限类型 (1-目录，2-菜单，3-按钮/接口)
     */
    private Integer permType;

    /**
     * 排序号 (用于前端菜单展示顺序)
     */
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
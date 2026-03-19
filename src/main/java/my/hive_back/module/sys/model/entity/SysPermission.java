package my.hive_back.module.sys.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("sys_permission")
@Data
public class SysPermission {

    /**
     * 权限主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantCode;

    private String roleCode;

    /**
     * 权限编码（全局唯一）
     * 格式：资源:操作，示例：user:add、order:view、order:*
     */
    private String permCode;

    /**
     * 权限名称
     * 示例：用户添加、订单查看、订单所有权限
     */
    private String permName;

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

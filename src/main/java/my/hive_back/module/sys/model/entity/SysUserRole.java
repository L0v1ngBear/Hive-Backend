package my.hive_back.module.sys.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
/**
 * SysUserRole 属于小程序后端系统模块，定义持久化实体结构，用于表字段映射。
 */
@Data
public class SysUserRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String tenantCode;

    private Long roleId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    private Integer isDeleted;
}

package my.hive_back.module.sys.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@TableName("sys_role")
@Data
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantCode;

    private String roleCode;

    private String roleName;

    private Integer isSystem;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;

    // --------------------- 非数据库字段 ---------------------
    @TableField(exist = false)
    private List<String> permCodes;
}
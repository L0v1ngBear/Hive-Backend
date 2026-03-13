package my.hive_back.module;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

public class BaseEntity {

    /**
     * 主键ID（雪花算法生成）
     */
    private Long id;

    /**
     * 租户ID（多租户隔离）
     */
    private String tenantCode;

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
     * 乐观锁版本号（防止并发更新）
     */
    @Version
    private Integer version;

    /**
     * 删除标记（0-未删，1-已删）
     */
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag = 0;
}

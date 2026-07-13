package my.hive_back.module.inventory.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;


/**
 * InventoryRecord 属于小程序后端库存模块，定义持久化实体结构，用于表字段映射。
 */
@Data
public class InventoryRecord {

    @TableId(type = IdType.AUTO)
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
     * 关联的布匹ID
     */
    private Long clothId;

    private String modelCode;

    /**
     * 操作类型（0-入库，1-出库）
     */
    private Integer operateType;

    /**
     * 操作米数（入库为新增米数，出库为减少米数）
     */
    private Float operateMeters;

    private Float remainingMeters;

    /**
     * 操作人ID（关联用户表）
     */
    private Long operatorId;


}

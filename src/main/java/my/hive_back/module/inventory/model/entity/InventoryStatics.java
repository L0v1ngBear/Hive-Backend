package my.hive_back.module.inventory.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存统计实体类
 * 数据来源于每日统计
 * 对应数据库表：inventory_statics
 */
@TableName("inventory_statics")
@Data
public class InventoryStatics {
    /**
     * 主键ID（自增）
     */
    @TableId(type = IdType.AUTO) // 主键策略：自增
    private Long id;

    /**
     * 统计日期（核心维度，唯一）
     * 对应数据库字段：stat_date
     */
    @TableField("stat_date")
    private LocalDateTime statDate;

    /**
     * 总卷数
     * 对应数据库字段：total_roll_count
     */
    @TableField("total_roll_count")
    private Long totalRollCount;

    /**
     * 总米数（用BigDecimal避免浮点精度丢失）
     * 对应数据库字段：total_meters
     */
    @TableField("total_meters")
    private Float totalMeters;

    /**
     * 日入米数
     * 对应数据库字段：day_in_meters
     */
    @TableField("day_in_meters")
    private Float dayInMeters;

    /**
     * 日出米数
     * 对应数据库字段：day_out_meters
     */
    @TableField("day_out_meters")
    private Float dayOutMeters;

    /**
     * 创建时间（自动填充）
     * 对应数据库字段：create_time
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT) // 插入时自动填充
    private LocalDateTime createTime;

    /**
     * 更新时间（自动填充）
     * 对应数据库字段：update_time
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE) // 插入/更新时自动填充
    private LocalDateTime updateTime;
}

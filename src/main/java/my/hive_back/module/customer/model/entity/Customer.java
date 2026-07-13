package my.hive_back.module.customer.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Customer 属于小程序后端客户模块，定义持久化实体结构，用于表字段映射。
 */
@Data
public class Customer {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户编码 (多租户隔离)
     */
    private String tenantCode;

    /**
     * 公司名称
     */
    private String customerName;

    /**
     * 客户类型
     */
    private Integer customerType;

    /**
     * 施工区域 (省份/城市)
     */
    private String constructionArea;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

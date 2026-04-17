package my.hive_back.module.inventory.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
/**
 * ClothModelSpec 属于小程序后端库存模块，定义持久化实体结构，用于表字段映射。
 */
@Data
@TableName("cloth_model_spec")
public class ClothModelSpec {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String modelCode;

    private Float spec;

    // 加唯一索引兜底：model_code + spec + tenant_code
    private String tenantCode;
}

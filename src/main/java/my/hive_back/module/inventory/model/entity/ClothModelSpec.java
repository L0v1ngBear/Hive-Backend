package my.hive_back.module.inventory.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import my.hive_back.module.BaseEntity;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("cloth_model_spec")
public class ClothModelSpec extends BaseEntity {

    private String modelCode;

    private String spec;

    private Integer sort;
}

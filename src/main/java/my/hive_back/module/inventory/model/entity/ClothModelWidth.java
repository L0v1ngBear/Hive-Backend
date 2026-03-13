package my.hive_back.module.inventory.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import my.hive_back.module.BaseEntity;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("cloth_model_width")
public class ClothModelWidth extends BaseEntity {

    private String modelCode;

    private String width;

    private Integer sort;
}

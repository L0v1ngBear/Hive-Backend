package my.hive_back.module.inventory.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import my.hive_back.module.BaseEntity;


/**
 * 库存出入记录实体类
 */
@EqualsAndHashCode(callSuper = true)
@TableName("inventory_record")
@Data
public class InventoryRecord extends BaseEntity {

    /**
     * 关联的布匹ID
     */
    private Long clothId;

    /**
     * 操作类型（1-入库，2-出库）
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
    private String operatorId;

    /**
     * 操作人名称（冗余）
     */
    private String operatorName;

    /**
     * 操作备注（如“扫码入库”“手动出库”）
     */
    private String remark;

}

package my.hive_back.module.inventory.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import my.hive_back.module.BaseEntity;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@TableName("cloth")
@Data
public class Cloth extends BaseEntity {

    /**
     * 布匹条码（唯一索引）
     */
    private String barcode;

    /**
     * 型号编码
     */
    private String modelCode;

    /**
     * 门幅（如1.8m）
     */
    private String width;

    /**
     * 总米数（高精度）
     */
    private Float meters;

    /**
     * 状态（0-在库，1-已出库，2-部分出库）
     */
    private Integer status;

    /** 总米数（高精度，8字节） */
    private Float totalMeters;

    /** 剩余米数（支持部分出库，8字节） */
    private Float remainingMeters;

    /**
     * 入库时间
     */
    private LocalDateTime inTime;

    /**
     * 出库时间（部分出库时为首次出库时间）
     */
    private LocalDateTime outTime;

    /**
     * 入库操作人ID（关联用户表）
     */
    private Long inOperatorId;

    /**
     * 出库操作人ID（关联用户表）
     */
    private Long outOperatorId;


    private String inType;
}

package my.hive_back.module.inventory.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("cloth")
@Data
public class Cloth {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantCode;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private String barcode;

    private String modelCode;

    private Float spec;

    private Float meters;

    private Integer status;

    private Float totalMeters;

    private Float remainingMeters;

    private LocalDateTime inTime;

    private LocalDateTime outTime;

    private Long inOperatorId;

    private Long outOperatorId;

    private String inType;

    private Integer isBad;

    @Version
    private Integer version;
}
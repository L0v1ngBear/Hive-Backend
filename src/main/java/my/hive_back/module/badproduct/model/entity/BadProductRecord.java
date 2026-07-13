package my.hive_back.module.badproduct.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * BadProductRecord 属于小程序后端坏品模块，定义持久化实体结构，用于表字段映射。
 */
@Data
@TableName("bad_product_record")
public class BadProductRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantCode;

    private String defectiveId;

    private String orderId;

    private String type;

    private Long creatorId;

    private String creatorName;

    private BigDecimal quantity;

    private BigDecimal lossAmount;

    private String description;

    /**
     * 负责跟进本次质量问题的人员。
     */
    @TableField("responsible_person")
    private String responsiblePerson;

    /**
     * 质量异常即时处理措施。
     */
    @TableField("process_measure")
    private String processMeasure;

    /**
     * 同类问题后续改进方案。
     */
    @TableField("improvement_plan")
    private String improvementPlan;

    private String attachmentName;

    private String attachmentUrl;

    private Long attachmentSize;

    private String status;

    private String processMethod;

    private String processRemark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

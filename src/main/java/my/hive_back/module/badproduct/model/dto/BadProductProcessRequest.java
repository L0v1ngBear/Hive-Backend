package my.hive_back.module.badproduct.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
/**
 * BadProductProcessRequest 属于小程序后端坏品模块，定义入参结构。
 */
@Data
public class BadProductProcessRequest {

    @NotBlank(message = "质量编号不能为空")
    private String defectiveId;

    @NotBlank(message = "处理方式不能为空")
    private String method;

    private String remark;

    @NotBlank(message = "负责人员不能为空")
    private String responsiblePerson;

    @NotBlank(message = "处理措施不能为空")
    private String processMeasure;

    @NotBlank(message = "改进方案不能为空")
    private String improvementPlan;
}

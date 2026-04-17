package my.hive_back.module.label.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
/**
 * LabelTemplateSaveRequest 属于小程序后端标签模块，定义入参结构。
 */
@Data
public class LabelTemplateSaveRequest {

    @NotBlank(message = "模板名称不能为空")
    private String name;

    private String printType = "label";

    @NotBlank(message = "模板内容不能为空")
    private String content;

    private Integer isDefault = 0;
}

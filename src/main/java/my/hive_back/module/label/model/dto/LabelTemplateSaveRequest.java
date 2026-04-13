package my.hive_back.module.label.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LabelTemplateSaveRequest {

    @NotBlank(message = "模板名称不能为空")
    private String name;

    private String printType = "label";

    @NotBlank(message = "模板内容不能为空")
    private String content;

    private Integer isDefault = 0;
}
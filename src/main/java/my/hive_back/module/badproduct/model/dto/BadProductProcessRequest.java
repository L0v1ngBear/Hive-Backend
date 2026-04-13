package my.hive_back.module.badproduct.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BadProductProcessRequest {

    @NotBlank(message = "次品编号不能为空")
    private String defectiveId;

    @NotBlank(message = "处理方式不能为空")
    private String method;

    private String remark;
}
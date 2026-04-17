package my.hive_back.module.badproduct.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
/**
 * BadProductProcessRequest 属于小程序后端坏品模块，定义入参结构。
 */
@Data
public class BadProductProcessRequest {

    @NotBlank(message = "次品编号不能为空")
    private String defectiveId;

    @NotBlank(message = "处理方式不能为空")
    private String method;

    private String remark;
}

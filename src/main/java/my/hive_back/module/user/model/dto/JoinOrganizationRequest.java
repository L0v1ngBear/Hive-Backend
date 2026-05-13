package my.hive_back.module.user.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JoinOrganizationRequest {

    @NotBlank(message = "组织邀请码不能为空")
    @Size(max = 12, message = "组织邀请码长度不能超过12个字符")
    private String joinCode;
}

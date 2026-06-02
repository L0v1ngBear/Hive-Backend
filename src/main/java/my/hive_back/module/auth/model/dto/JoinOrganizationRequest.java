package my.hive_back.module.auth.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 小程序用户加入组织入参。
 */
@Data
public class JoinOrganizationRequest {

    @NotBlank(message = "请输入姓名")
    @Size(max = 30, message = "姓名不能超过30个字符")
    private String name;

    @NotBlank(message = "请输入组织码")
    @Size(max = 32, message = "组织码不能超过32个字符")
    private String organizationCode;
}

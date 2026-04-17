package my.hive_back.module.tenant.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
/**
 * TenantLocationAddRequest 属于小程序后端租户模块，定义入参结构。
 */
@Data
public class TenantLocationAddRequest {

    @NotBlank
    private String tenantName;

    @NotBlank
    private String address;
}

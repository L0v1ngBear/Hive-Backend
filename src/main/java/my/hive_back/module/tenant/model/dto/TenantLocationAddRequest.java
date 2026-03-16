package my.hive_back.module.tenant.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TenantLocationAddRequest {

    @NotBlank
    private String tenantName;

    @NotBlank
    private String address;
}

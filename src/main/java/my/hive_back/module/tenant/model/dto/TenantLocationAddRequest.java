package my.hive_back.module.tenant.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
/**
 * TenantLocationAddRequest 属于小程序后端租户模块，定义入参结构。
 */
@Data
public class TenantLocationAddRequest {

    private String address;

    @NotNull(message = "纬度不能为空")
    private Double latitude;

    @NotNull(message = "经度不能为空")
    private Double longitude;

    private Double radius;
}

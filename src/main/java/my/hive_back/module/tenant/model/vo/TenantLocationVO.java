package my.hive_back.module.tenant.model.vo;


import lombok.Data;

import java.math.BigDecimal;
/**
 * TenantLocationVO 属于小程序后端租户模块，定义出参结构。
 */
@Data
public class TenantLocationVO {

    private BigDecimal latitude;

    private BigDecimal longitude;

    private Integer status;
}

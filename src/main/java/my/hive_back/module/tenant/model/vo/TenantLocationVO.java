package my.hive_back.module.tenant.model.vo;


import lombok.Data;

import java.math.BigDecimal;

@Data
public class TenantLocationVO {

    private BigDecimal latitude;

    private BigDecimal longitude;

    private Integer status;
}

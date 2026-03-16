package my.hive_back.module.tenant.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@TableName("tenant_location")
@Data
public class TenantLocation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String tenantCode;

    private String tenantName;

    private Integer status;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private String address;

    private Integer radius;
}

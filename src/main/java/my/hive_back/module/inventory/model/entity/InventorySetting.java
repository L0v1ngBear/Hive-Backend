package my.hive_back.module.inventory.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("inventory_setting")
public class InventorySetting {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantCode;

    private BigDecimal warningThresholdMeters;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

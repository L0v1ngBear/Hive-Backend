package my.hive_back.module.order.model.dto;

import lombok.Data;
import org.hibernate.validator.constraints.Range;

@Data
public class ProductionOrderUpdateRequest {

    private String status;

    @Range(min = 0, max = 4, message = "工序索引超出范围")
    private Integer process;
}
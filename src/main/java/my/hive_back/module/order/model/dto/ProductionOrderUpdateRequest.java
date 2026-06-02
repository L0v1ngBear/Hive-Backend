package my.hive_back.module.order.model.dto;

import lombok.Data;
import org.hibernate.validator.constraints.Range;
/**
 * ProductionOrderUpdateRequest 属于小程序后端订单模块，定义入参结构。
 */
@Data
public class ProductionOrderUpdateRequest {

    private String status;

    @Range(min = 0, max = 9, message = "工序索引超出范围")
    private Integer process;

    /** 操作类型：status_change / process_change / scan_change 等 */
    private String operateType;

    /** 本次更新备注，会写入生产订单状态日志 */
    private String remark;
}

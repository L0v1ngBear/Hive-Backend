package my.hive_back.module.order.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
/**
 * ProductionOrderVO 属于小程序后端订单模块，定义出参结构。
 */
@Data
public class ProductionOrderVO {

    private Long id;

    private String orderId;

    private String salesOrderId;

    private String status;

    private String orderCategory;

    private String modelCode;

    private Float weight;

    private Float spec;

    private Integer quantity;

    /** 当前生产工序：ProcessEnum */
    private Integer process;

    private String processText;

    private String currentProcessText;

    private String completedProcessText;

    private Integer processProgressPercent;

    private List<ProductionProcessStepVO> processSteps;

    private String customerName;

    private String projectName;

    private String brandName;

    private String informationChannel;

    private LocalDateTime createTime;
}

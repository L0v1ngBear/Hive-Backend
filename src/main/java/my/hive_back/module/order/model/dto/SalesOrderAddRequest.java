package my.hive_back.module.order.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

import java.util.List;

/**
 * 新建订单 请求DTO
 */
@Data
public class SalesOrderAddRequest {

    /**
     * 客户名称
     */
    @NotBlank(message = "客户名称不能为空")
    private String customerName;

    @NotBlank(message = "项目名称不能为空")
    private String projectName;

    // 这里不再是单独的 needGood 和 quantity，而是一个集合
    @NotEmpty(message = "订单商品不能为空")
    @Valid // 嵌套校验
    private List<OrderItemDTO> items;

    /**
     * 预计发货日期
     */
    private String deliveryDate;

    /**
     * 是否同步创建生产订单 0-否 1-是
     */
    private Integer createProductionOrder;

    @Data
    public class OrderItemDTO {
        @NotBlank(message = "商品型号不能为空")
        private String modelCode;

        @NotNull(message = "数量不能为空")
        private BigDecimal quantity;

    }
}
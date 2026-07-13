package my.hive_back.module.order.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

import java.util.List;

/**
 * SalesOrderAddRequest 属于小程序后端订单模块，定义入参结构。
 */
@Data
@ValidSalesOrderInformationChannel
public class SalesOrderAddRequest {

    /**
     * 客户名称
     */
    @NotBlank(message = "客户名称不能为空")
    private String customerName;

    @NotBlank(message = "项目名称不能为空")
    private String projectName;

    private String brandName;

    private String orderCategory;

    // 这里不再是单独的 needGood 和 quantity，而是一个集合；允许暂不填写明细，后续再补充。
    @Valid // 嵌套校验
    private List<OrderItemDTO> items;

    /**
     * 信息渠道
     */
    private String informationChannel;

    /**
     * 是否同步创建生产订单 0-否 1-是
     */
    private Integer createProductionOrder;
    @Data
    public class OrderItemDTO {

        private String modelCode;

        private BigDecimal quantity;

        @Schema(description = "商品类别")
        private String weight;

        @Size(max = 50, message = "规格长度不能超过50个字符")
        private String spec;

    }
}

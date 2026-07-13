package my.hive_back.module.order.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

import java.math.BigDecimal;
import java.util.List;

/**
 * Unified order status and fulfillment update request.
 */
@Data
@ValidSalesOrderInformationChannel
public class UnifiedOrderUpdateRequest {

    @Pattern(
            regexp = "^(budgeting|budget_completed|pending_confirm|pending_pay|pending_material|producing|pending_ship|shipped|completed|pending_cancel|cancelled)$",
            message = "目标状态不合法"
    )
    private String status;

    @Range(min = 0, max = 9, message = "工序索引超出范围")
    private Integer process;

    private String operateType;

    private String remark;

    private String informationChannel;

    @Size(max = 100)
    private String customerName;

    @Size(max = 100)
    private String projectName;

    @Size(max = 100)
    private String brandName;

    private String orderCategory;

    @Valid
    private List<OrderItemDTO> items;

    private List<Long> auditorIds;

    @Valid
    private ExpressInfo expressInfo;

    private Integer isInvoice;

    @Data
    public static class ExpressInfo {

        @Size(max = 50, message = "物流公司名称长度不能超过50个字符")
        private String expressCompany;

        @Size(max = 50, message = "物流单号长度不能超过50个字符")
        private String expressNo;
    }

    @Data
    public static class OrderItemDTO {

        private String modelCode;

        private BigDecimal quantity;

        private String weight;

        @Size(max = 50, message = "规格长度不能超过50个字符")
        private String spec;
    }
}

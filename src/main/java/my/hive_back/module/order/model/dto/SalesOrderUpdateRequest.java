package my.hive_back.module.order.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 销售订单状态流转入参。
 */
@Data
public class SalesOrderUpdateRequest {

    @NotBlank(message = "目标状态不能为空")
    @Pattern(
            regexp = "^(pending_confirm|pending_pay|pending_material|producing|pending_ship|shipped|completed)$",
            message = "目标状态不合法"
    )
    private String status;

    @Valid
    private ExpressInfo expressInfo;

    /**
     * 是否开票。状态流转本身不强制传入，只有前端明确修改时才更新。
     */
    private Integer isInvoice;

    /**
     * 发货物流信息。仅流转到已发货时必填。
     */
    @Data
    public static class ExpressInfo {

        @Size(max = 50, message = "物流公司名称长度不能超过50个字符")
        private String expressCompany;

        @Size(max = 50, message = "物流单号长度不能超过50个字符")
        private String expressNo;
    }
}

package my.hive_back.module.order.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
/**
 * SalesOrderUpdateRequest 属于小程序后端订单模块，定义入参结构。
 */
@Data
public class SalesOrderUpdateRequest {

    @NotBlank(message = "目标状态不能为空")
    @Pattern(regexp = "^(pending_ship|shipped|completed)$", message = "目标状态仅支持：pending_ship、shipped、completed")
    private String status;


    @Valid // 开启嵌套对象校验
    private ExpressInfo expressInfo;

    @NotBlank(message = "是否开具发票不能为空")
    private Integer isInvoice;

    /**
     * 物流信息内部类（带校验）
     */
    @Data
    public static class ExpressInfo {
        /**
         * 物流公司：非空 + 长度限制
         */
        @Size(max = 50, message = "物流公司名称长度不能超过50字符")
        private String expressCompany;

        /**
         * 物流单号：非空 + 长度限制
         */
        @Size(max = 50, message = "物流单号长度不能超过50字符")
        private String expressNo;
    }
}

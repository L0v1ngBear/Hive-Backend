package my.hive_back.module.order.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SalesOrderStatusRequest {

    @NotBlank(message = "目标状态不能为空")
    @Pattern(regexp = "^(pending_ship|shipped|completed)$", message = "目标状态仅支持：pending_ship、shipped、completed")
    private String status;

    @Size(max = 500, message = "操作备注长度不能超过500字符")
    private String remark = "";

    @Valid // 开启嵌套对象校验
    private ExpressInfo expressInfo;

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

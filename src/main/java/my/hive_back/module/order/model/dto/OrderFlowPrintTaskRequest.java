package my.hive_back.module.order.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 小程序创建订单流转码打印任务请求。
 */
@Data
public class OrderFlowPrintTaskRequest {

    @NotBlank(message = "订单号不能为空")
    private String orderId;
}

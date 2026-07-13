package my.hive_back.module.order.model.vo;

import lombok.Data;

import java.util.Map;

/**
 * 小程序订单流转码打印任务响应。
 */
@Data
public class OrderFlowPrintTaskVO {

    private String taskNo;

    private String orderId;

    private String orderType;

    private String printType;

    private Map<String, Object> printPayload;
}

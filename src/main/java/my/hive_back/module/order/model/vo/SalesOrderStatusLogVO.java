package my.hive_back.module.order.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 销售订单状态日志出参，用于小程序订单详情展示流转时间轴。
 */
@Data
public class SalesOrderStatusLogVO {

    private Long id;

    private String tenantCode;

    private String orderId;

    private String oldStatus;

    private String newStatus;

    private String operateType;

    private String remark;

    private String operator;

    private String operatorName;

    private LocalDateTime createTime;
}

package my.hive_back.module.approval.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Order approval row. It represents orders waiting for business confirmation.
 */
@Data
public class OrderApprovalVO {

    private String orderType;

    private String orderTypeText;

    private String orderId;

    private String customerName;

    private String projectName;

    private String summary;

    private String status;

    private String statusText;

    private LocalDateTime createTime;

    private Long auditorId;

    private String auditorIds;

    private String auditorName;

    private Boolean canAudit;
}

package my.hive_back.module.finance.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FinanceApprovalVO {

    private Long id;

    private String approvalCode;

    private String category;

    private BigDecimal amount;

    private String reason;

    private String attachmentUrl;

    private Integer status;

    private String statusText;

    private Long applyUserId;

    private String applyUserName;

    private String applyDepartmentName;

    private Long auditorId;

    private String auditorName;

    private String auditComment;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
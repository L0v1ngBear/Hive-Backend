package my.hive_back.module.approval.model.vo;

import lombok.Data;

/**
 * Approval center pending counters used by red-dot badges.
 */
@Data
public class ApprovalSummaryVO {

    private long leavePending;

    private long financePending;

    private long resignationPending;

    private long orderPending;

    private long qualityPending;

    private long totalPending;

    private boolean canCreateFinance;

    private boolean canCreateLeave;

    private boolean canCreateResignation;

    private boolean canViewOrder;

    private boolean canViewQuality;

    private boolean canReviewFinance;

    private boolean canReviewLeave;

    private boolean canReviewResignation;
}

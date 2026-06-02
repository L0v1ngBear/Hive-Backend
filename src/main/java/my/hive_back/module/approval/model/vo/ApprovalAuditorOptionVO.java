package my.hive_back.module.approval.model.vo;

import lombok.Data;

/**
 * 小程序审批人选择项。
 */
@Data
public class ApprovalAuditorOptionVO {

    private Long id;
    private String name;
    private String departmentName;
    private String positionName;
    private Boolean defaultAuditor;
}

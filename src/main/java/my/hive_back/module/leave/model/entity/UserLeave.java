package my.hive_back.module.leave.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
/**
 * UserLeave 属于小程序后端请假模块，定义持久化实体结构，用于表字段映射。
 */
@TableName
@Data
public class UserLeave {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String leaveCode;     // 请假编码
    private String tenantCode;
    private Long applyUserId;     // 申请人ID
    private Integer leaveType;    // 请假类型
    private LocalDateTime startTime; // 开始时间
    private LocalDateTime endTime;   // 结束时间
    private String reason;        // 请假事由
    private Integer status;       // 审批状态 (0-待审批, 1-已同意, 2-已拒绝)
    private String auditComment;  // 审批意见
    private LocalDateTime updateTime;
    private Long auditorId;       // 当前审批人/处理人ID
    private LocalDateTime createTime;
}

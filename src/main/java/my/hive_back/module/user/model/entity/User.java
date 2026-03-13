package my.hive_back.module.user.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("user")
@Data
public class User {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户编码 (用于多租户数据隔离)
     */
    private String tenantCode;

    /**
     * 姓名
     */
    private String name;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 部门名称
     * (注：如果是规范的数据库设计，这里也可以加一个 departmentId 关联部门表)
     */
    private String departmentName;

    /**
     * 工作岗位 (如：销售专员、生产主管等)
     */
    private String position;

    /**
     * 直属上级ID (用于查询上下级关系，0或null表示没有上级/最高级)
     */
    private Long managerId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
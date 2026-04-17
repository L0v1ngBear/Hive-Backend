package my.hive_back.module.user.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
/**
 * User 属于小程序后端用户模块，定义持久化实体结构，用于表字段映射。
 */
@TableName
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
     * 登录账号
     */
    private String loginName;

    /**
     * 登录密码
     */
    private String password;

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
     * 角色级别
     */
    private Integer roleLevel;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 状态 (0: 离职, 1: 在职， 2：试用)
     */
    private Integer status;
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}

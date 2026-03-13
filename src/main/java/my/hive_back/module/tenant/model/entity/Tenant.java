package my.hive_back.module.tenant.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户实体类
 * 对应数据库表：tenant
 * 功能说明：存储租户（客户/商户）的基础信息，用于多租户系统的租户管理
 *
 */
@TableName("tenant") // 关联数据库表名：tenant
@Data
public class Tenant {

    /**
     * 租户ID
     * 主键，自增（数据库层面配置自增，MyBatis-Plus默认使用雪花算法，如需自增需加@TableId(type = IdType.AUTO)）
     */
    private Long id;

    /**
     * 租户编码
     * 唯一标识，格式规范：如TENANT_001、TEST_002，用于租户隔离（非主键）
     */
    private String tenantCode;

    /**
     * 租户名称
     * 租户的业务名称（如"XX科技有限公司"），用于前端展示
     */
    private String tenantName;

    /**
     * 租户类型
     * 枚举值：1-普通租户，2-vip租户，3-超级租户，用于区分租户权限范围
     */
    private Integer tenantType;

    /**
     * 联系人
     * 租户对接人的姓名（企业租户必填，个人租户为本人姓名）
     */
    private String contactPerson;

    /**
     * 联系电话
     * 租户对接人的手机号，格式：11位数字，用于系统通知/验证码发送
     */
    private String contactPhone;

    /**
     * 租户登录密码
     * 加密存储（推荐BCrypt加密），用于租户后台登录验证
     */
    private String password;

    /**
     * 租户状态
     * 枚举值：0-禁用（无法登录），1-启用（正常使用），2-冻结（违规冻结），3-待审核
     */
    private Integer status;

    /**
     * 创建人ID
     * 关联系统用户表的主键，记录该租户的创建者（管理员ID）
     */
    private Long creator;

    /**
     * 创建时间
     * 租户记录的创建时间，默认值：当前时间（数据库层面可配置DEFAULT CURRENT_TIMESTAMP）
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     * 租户记录的最后修改时间，默认值：创建时间，更新时自动更新（数据库层面可配置ON UPDATE CURRENT_TIMESTAMP）
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标识
     * 枚举值：0-未删除（正常数据），1-已删除（软删除，查询时过滤）
     * 说明：MyBatis-Plus逻辑删除需配置@TableLogic注解，默认字段为deleted
     */
    private Integer deleted;
}
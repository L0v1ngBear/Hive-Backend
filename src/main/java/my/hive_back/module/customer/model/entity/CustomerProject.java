package my.hive_back.module.customer.model.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 客户-合作项目实体类
 */
@TableName("customer_project")
@Data
public class CustomerProject {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantCode;

    /**
     * 关联的客户ID
     */
    private Long customerId;

    /**
     * 合作项目名称
     */
    private String projectName;

}
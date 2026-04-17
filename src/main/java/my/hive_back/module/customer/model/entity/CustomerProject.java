package my.hive_back.module.customer.model.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * CustomerProject 属于小程序后端客户模块，定义持久化实体结构，用于表字段映射。
 */
@TableName
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

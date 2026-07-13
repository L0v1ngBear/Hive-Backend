package my.hive_back.module.customer.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
/**
 * CustomerContact 属于小程序后端客户模块，定义持久化实体结构，用于表字段映射。
 */
@TableName
@Data
public class CustomerContact {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantCode;

    /**
     * 关联的客户ID
     */
    private Long customerId;

    /**
     * 联系人姓名
     */
    private String contactName;

    /**
     * 联系电话
     */
    private String contactPhone;
}

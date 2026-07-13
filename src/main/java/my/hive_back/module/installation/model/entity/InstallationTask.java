package my.hive_back.module.installation.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("installation_task")
public class InstallationTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tenant_code")
    private String tenantCode;

    @TableField("order_id")
    private String orderId;

    @TableField("order_status")
    private String orderStatus;

    @TableField("installation_status")
    private String installationStatus;

    @TableField("customer_name")
    private String customerName;

    @TableField("customer_phone")
    private String customerPhone;

    @TableField("project_name")
    private String projectName;

    @TableField("brand_name")
    private String brandName;

    @TableField("order_category")
    private String orderCategory;

    @TableField("goods_desc")
    private String goodsDesc;

    @TableField("total_quantity")
    private Integer totalQuantity;

    @TableField("information_channel")
    private String informationChannel;

    @TableField("express_company")
    private String expressCompany;

    @TableField("express_no")
    private String expressNo;

    @TableField("is_invoice")
    private Integer isInvoice;

    private String creator;

    private String remark;

    @TableField("order_attachment_name")
    private String orderAttachmentName;

    @TableField("order_attachment_url")
    private String orderAttachmentUrl;

    @TableField("order_attachment_size")
    private Long orderAttachmentSize;

    @TableField("order_completed_time")
    private LocalDateTime orderCompletedTime;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}

package my.hive_back.module.installation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import my.hive_back.module.installation.model.entity.InstallationTask;
import org.apache.ibatis.annotations.Insert;

public interface InstallationTaskMapper extends BaseMapper<InstallationTask> {

    @Insert("""
            INSERT INTO installation_task (
                tenant_code, order_id, order_status, installation_status,
                customer_name, customer_phone, project_name, brand_name,
                order_category, goods_desc, total_quantity, delivery_date,
                express_company, express_no, is_invoice, creator, remark,
                order_attachment_name, order_attachment_url, order_attachment_size,
                order_completed_time, create_time, update_time
            ) VALUES (
                #{tenantCode}, #{orderId}, #{orderStatus}, #{installationStatus},
                #{customerName}, #{customerPhone}, #{projectName}, #{brandName},
                #{orderCategory}, #{goodsDesc}, #{totalQuantity}, #{deliveryDate},
                #{expressCompany}, #{expressNo}, #{isInvoice}, #{creator}, #{remark},
                #{orderAttachmentName}, #{orderAttachmentUrl}, #{orderAttachmentSize},
                #{orderCompletedTime}, #{createTime}, #{updateTime}
            )
            ON DUPLICATE KEY UPDATE
                order_status = VALUES(order_status),
                customer_name = VALUES(customer_name),
                customer_phone = VALUES(customer_phone),
                project_name = VALUES(project_name),
                brand_name = VALUES(brand_name),
                order_category = VALUES(order_category),
                goods_desc = VALUES(goods_desc),
                total_quantity = VALUES(total_quantity),
                delivery_date = VALUES(delivery_date),
                express_company = VALUES(express_company),
                express_no = VALUES(express_no),
                is_invoice = VALUES(is_invoice),
                creator = VALUES(creator),
                remark = VALUES(remark),
                order_attachment_name = VALUES(order_attachment_name),
                order_attachment_url = VALUES(order_attachment_url),
                order_attachment_size = VALUES(order_attachment_size),
                order_completed_time = COALESCE(order_completed_time, VALUES(order_completed_time)),
                update_time = VALUES(update_time)
            """)
    int upsertFromCompletedOrder(InstallationTask task);
}

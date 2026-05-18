package my.hive_back.module.order.mapper;

import my.hive_back.module.order.model.entity.SalesOrder;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * SalesOrderMapper 属于小程序后端订单模块，是数据访问类，负责与数据库交互。
 */
public interface SalesOrderMapper extends BaseMapper<SalesOrder> {

    @Update("UPDATE sales_order SET status = #{order.status} " +
            ",express_company = #{order.expressCompany} " +
            ",express_no = #{order.expressNo} " +
            ",updater = #{order.updater} " +
            "WHERE order_id = #{order.orderId} " +
            "AND status = #{oldStatus} ")
    int updateStatus(SalesOrder order, String oldStatus);

    @Select("""
            SELECT order_id, tenant_code, status, customer_name, project_name, goods_desc,
                   total_amount, total_quantity, delivery_date, express_company, express_no,
                   is_invoice, creator, updater, create_time, update_time
            FROM sales_order
            WHERE order_id = #{orderId}
            LIMIT 1
            """)
    SalesOrder selectByOrderId(String orderId);
}

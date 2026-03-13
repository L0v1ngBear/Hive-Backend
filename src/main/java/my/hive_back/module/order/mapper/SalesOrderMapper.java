package my.hive_back.module.order.mapper;

import my.hive_back.module.order.model.dto.SalesOrderStatusRequest;
import my.hive_back.module.order.model.entity.SalesOrder;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface SalesOrderMapper extends BaseMapper<SalesOrder> {

    @Update("UPDATE sales_order SET status = #{order.status} " +
            ",express_company = #{order.expressCompany} " +
            "AND express_no = #{order.expressNo} " +
            "WHERE order_id = #{order.orderId} " +
            "AND status = #{oldStatus} ")
    int updateStatus(SalesOrder order, String oldStatus);

    @Select("SELECT * FROM sales_order WHERE order_id = #{orderId}")
    SalesOrder selectByOrderId(String orderId);
}

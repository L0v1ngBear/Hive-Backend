package my.hive_back.module.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import my.hive_back.module.order.model.entity.ProductionOrder;
import org.apache.ibatis.annotations.Select;

/**
 * ProductionOrderMapper 属于小程序后端订单模块，是数据访问类，负责与数据库交互。
 */
public interface ProductionOrderMapper extends BaseMapper<ProductionOrder> {

    @Select("SELECT * FROM production_order WHERE order_id = #{orderId} FOR UPDATE")
    ProductionOrder selectByOrderId(String orderId);
}

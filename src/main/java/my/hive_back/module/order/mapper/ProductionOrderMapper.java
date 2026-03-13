package my.hive_back.module.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import my.hive_back.module.order.model.entity.ProductionOrder;
import org.apache.ibatis.annotations.Select;

public interface ProductionOrderMapper extends BaseMapper<ProductionOrder> {

    @Select("SELECT * FROM production_order WHERE order_id = #{orderId} FOR UPDATE")
    ProductionOrder selectByOrderId(String orderId);
}

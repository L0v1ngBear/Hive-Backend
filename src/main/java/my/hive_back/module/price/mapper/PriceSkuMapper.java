package my.hive_back.module.price.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface PriceSkuMapper {

    @Select("SELECT COALESCE(base_price, 0) FROM price_sku WHERE tenant_code = #{tenantCode} AND model_code = #{modelCode} AND status = 1 AND is_deleted = 0 ORDER BY effective_date DESC, id DESC LIMIT 1")
    BigDecimal getPrice(@Param("tenantCode") String tenantCode, @Param("modelCode") String modelCode);
}
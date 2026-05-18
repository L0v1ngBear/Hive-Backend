package my.hive_back.module.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import my.hive_back.module.inventory.model.entity.Cloth;
import my.hive_back.module.inventory.model.vo.InventoryRecordVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * ClothMapper 属于小程序后端库存模块，是数据访问类，负责与数据库交互。
 */
public interface ClothMapper extends BaseMapper<Cloth> {

    @Select({
            "SELECT COUNT(1) FROM (",
            "SELECT model_code FROM cloth ",
            "WHERE tenant_code = #{tenantCode} AND remaining_meters > 0 ",
            "GROUP BY model_code ",
            "HAVING SUM(remaining_meters) <= #{threshold}",
            ") t"
    })
    Long countWarningModels(@Param("tenantCode") String tenantCode, @Param("threshold") BigDecimal threshold);

    @Select({
            "SELECT MIN(id) AS id, model_code AS modelCode, ",
            "CAST(COALESCE(SUM(remaining_meters), 0) AS DECIMAL(18, 2)) AS meters, ",
            "MAX(update_time) AS createTime ",
            "FROM cloth ",
            "WHERE tenant_code = #{tenantCode} AND remaining_meters > 0 ",
            "GROUP BY model_code ",
            "HAVING SUM(remaining_meters) <= #{threshold} ",
            "ORDER BY meters ASC, createTime DESC ",
            "LIMIT #{limit}"
    })
    List<InventoryRecordVO> selectWarningModels(@Param("tenantCode") String tenantCode,
                                                @Param("threshold") BigDecimal threshold,
                                                @Param("limit") Integer limit);
}

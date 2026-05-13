package my.hive_back.module.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import my.hive_back.module.inventory.model.entity.InventoryRecord;
import my.hive_back.module.inventory.model.vo.InventoryDailyMetersVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * InventoryRecordMapper 属于小程序后端库存模块，是数据访问类，负责与数据库交互。
 */
public interface InventoryRecordMapper extends BaseMapper<InventoryRecord> {

    @Select({
            "SELECT ",
            "COALESCE(SUM(CASE WHEN operate_type IN (#{inType}, #{externalImportType}) THEN operate_meters ELSE 0 END), 0) AS inMeters, ",
            "COALESCE(SUM(CASE WHEN operate_type = #{outType} THEN operate_meters ELSE 0 END), 0) AS outMeters ",
            "FROM inventory_record ",
            "WHERE tenant_code = #{tenantCode} ",
            "AND create_time >= #{startTime} ",
            "AND create_time < #{endTime}"
    })
    InventoryDailyMetersVO sumDailyMeters(@Param("tenantCode") String tenantCode,
                                          @Param("inType") Integer inType,
                                          @Param("externalImportType") Integer externalImportType,
                                          @Param("outType") Integer outType,
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime);
}

package my.hive_back.module.inventory.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import my.hive_back.module.inventory.model.entity.Cloth;
import my.hive_back.module.inventory.model.vo.InventoryModelSummaryVO;
import my.hive_back.module.inventory.model.vo.InventoryRecordVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * ClothMapper 属于小程序后端库存模块，是数据访问类，负责与数据库交互。
 */
public interface ClothMapper extends BaseMapper<Cloth> {

    @InterceptorIgnore(tenantLine = "true")
    @Select({
            "SELECT COUNT(1) FROM (",
            "SELECT model_code FROM cloth ",
            "WHERE tenant_code = #{tenantCode} AND remaining_meters > 0 ",
            "GROUP BY model_code ",
            "HAVING SUM(remaining_meters) <= #{threshold}",
            ") t"
    })
    Long countWarningModels(@Param("tenantCode") String tenantCode, @Param("threshold") BigDecimal threshold);

    @InterceptorIgnore(tenantLine = "true")
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

    @InterceptorIgnore(tenantLine = "true")
    @Select({
            "<script>",
            "SELECT model_code AS modelCode, spec AS spec, COUNT(1) AS rollCount,",
            "       CAST(COALESCE(SUM(total_meters), 0) AS DECIMAL(18, 2)) AS totalMeters,",
            "       CAST(COALESCE(SUM(remaining_meters), 0) AS DECIMAL(18, 2)) AS remainingMeters,",
            "       MAX(update_time) AS latestTime",
            "FROM cloth",
            "WHERE tenant_code = #{tenantCode}",
            "<if test='status != null'> AND status = #{status}</if>",
            "<if test='status == null'> AND remaining_meters &gt; 0</if>",
            "<if test='keyword != null and keyword != \"\"'>",
            "  AND (model_code LIKE CONCAT('%', #{keyword}, '%') OR barcode LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "GROUP BY model_code, spec",
            "<choose>",
            "  <when test='timeOrder == \"lifo\"'>ORDER BY MAX(COALESCE(in_time, create_time, update_time)) DESC, model_code ASC</when>",
            "  <otherwise>ORDER BY MIN(COALESCE(in_time, create_time, update_time)) ASC, model_code ASC</otherwise>",
            "</choose>",
            "</script>"
    })
    Page<InventoryModelSummaryVO> selectModelSummaryPage(Page<InventoryModelSummaryVO> page,
                                                         @Param("tenantCode") String tenantCode,
                                                         @Param("keyword") String keyword,
                                                         @Param("status") Integer status,
                                                         @Param("timeOrder") String timeOrder);
}

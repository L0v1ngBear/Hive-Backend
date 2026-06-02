package my.hive_back.module.todo.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import my.hive_back.module.todo.model.vo.TodoNotificationRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 小程序待办统一读取管理端沉淀的 notification_record。
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface TodoNotificationMapper {

    @Select("""
            SELECT
                id,
                biz_type AS bizType,
                biz_id AS bizId,
                title,
                content,
                level,
                route,
                task_status AS taskStatus,
                create_time AS createTime,
                update_time AS updateTime
            FROM notification_record
            WHERE tenant_code = #{tenantCode}
              AND status = 1
              AND (
                (task_status IS NULL AND read_flag = 0)
                OR task_status IN ('PENDING', 'PROCESSING')
              )
              AND NOT (biz_type = 'ANNOUNCEMENT' AND receiver_user_id IS NULL)
              AND (biz_type != 'AI_ADVICE' OR receiver_user_id = #{userId})
              AND (receiver_user_id IS NULL OR receiver_user_id = #{userId})
            ORDER BY
              CASE level WHEN 'critical' THEN 1 WHEN 'warning' THEN 2 WHEN 'info' THEN 3 ELSE 4 END,
              update_time DESC
            LIMIT #{limit}
            """)
    List<TodoNotificationRow> selectPending(@Param("tenantCode") String tenantCode,
                                            @Param("userId") Long userId,
                                            @Param("limit") Integer limit);

    @Select("""
            SELECT COUNT(1)
            FROM notification_record
            WHERE tenant_code = #{tenantCode}
              AND status = 1
              AND (
                (task_status IS NULL AND read_flag = 0)
                OR task_status IN ('PENDING', 'PROCESSING')
              )
              AND NOT (biz_type = 'ANNOUNCEMENT' AND receiver_user_id IS NULL)
              AND (biz_type != 'AI_ADVICE' OR receiver_user_id = #{userId})
              AND (receiver_user_id IS NULL OR receiver_user_id = #{userId})
            """)
    Long countPending(@Param("tenantCode") String tenantCode, @Param("userId") Long userId);
}

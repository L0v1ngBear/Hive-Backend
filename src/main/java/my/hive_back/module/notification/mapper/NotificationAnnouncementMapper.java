package my.hive_back.module.notification.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import my.hive_back.module.notification.model.vo.AnnouncementVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 小程序公告读取管理端沉淀的 enterprise_announcement。
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface NotificationAnnouncementMapper {

    @Select("""
            SELECT
                parent.id,
                parent.announcement_code AS bizId,
                parent.title,
                parent.content,
                parent.level,
                COALESCE(receiver.read_flag, 0) AS readFlag,
                receiver.read_time AS readTime,
                parent.create_time AS createTime,
                parent.update_time AS updateTime
            FROM enterprise_announcement parent
            LEFT JOIN enterprise_announcement_read receiver
              ON BINARY receiver.tenant_code = BINARY parent.tenant_code
             AND receiver.announcement_id = parent.id
             AND receiver.user_id = #{userId}
            WHERE BINARY parent.tenant_code = BINARY #{tenantCode}
              AND parent.status = 1
            ORDER BY parent.update_time DESC, parent.id DESC
            LIMIT #{limit}
            """)
    List<AnnouncementVO> selectRecentAnnouncements(@Param("tenantCode") String tenantCode,
                                                   @Param("userId") Long userId,
                                                   @Param("limit") Integer limit);

    @Update("""
            UPDATE enterprise_announcement_read
            SET read_flag = 1,
                read_time = COALESCE(read_time, NOW()),
                update_time = NOW()
            WHERE BINARY tenant_code = BINARY #{tenantCode}
              AND announcement_code = #{bizId}
              AND user_id = #{userId}
            """)
    int markReadByBizId(@Param("tenantCode") String tenantCode,
                        @Param("userId") Long userId,
                        @Param("bizId") String bizId);

    @Insert("""
            INSERT INTO enterprise_announcement_read (
                tenant_code, announcement_id, announcement_code, user_id, read_flag, read_time, create_time, update_time
            )
            SELECT
                a.tenant_code, a.id, a.announcement_code, #{userId}, 1, NOW(), NOW(), NOW()
            FROM enterprise_announcement a
            WHERE BINARY a.tenant_code = BINARY #{tenantCode}
              AND a.announcement_code = #{bizId}
              AND a.status = 1
            LIMIT 1
            ON DUPLICATE KEY UPDATE
                read_flag = 1,
                read_time = COALESCE(read_time, NOW()),
                update_time = NOW()
            """)
    int upsertReadByBizId(@Param("tenantCode") String tenantCode,
                          @Param("userId") Long userId,
                          @Param("bizId") String bizId);
}

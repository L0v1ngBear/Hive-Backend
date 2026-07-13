package my.hive_back.module.notification.service;

import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import my.hive_back.module.notification.mapper.NotificationAnnouncementMapper;
import my.hive_back.module.notification.model.vo.AnnouncementVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 小程序企业公告服务。
 */
@Service
public class NotificationAnnouncementService {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 50;

    @Resource
    private NotificationAnnouncementMapper notificationAnnouncementMapper;

    public List<AnnouncementVO> announcements(Integer limit) {
        String tenantCode = TenantPermissionContext.getTenantCode();
        Long userId = TenantPermissionContext.getUserId();
        if (tenantCode == null || tenantCode.isBlank() || userId == null) {
            return List.of();
        }
        int safeLimit = normalizeLimit(limit);
        List<AnnouncementVO> announcements = notificationAnnouncementMapper.selectRecentAnnouncements(tenantCode, userId, safeLimit);
        for (AnnouncementVO announcement : announcements) {
            String bizId = announcement.getBizId();
            if (bizId == null || bizId.isBlank()) {
                continue;
            }
            int updated = notificationAnnouncementMapper.markReadByBizId(tenantCode, userId, bizId);
            if (updated <= 0) {
                updated = notificationAnnouncementMapper.upsertReadByBizId(tenantCode, userId, bizId);
            }
            if (updated > 0) {
                announcement.setReadFlag(1);
            }
        }
        return announcements;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}

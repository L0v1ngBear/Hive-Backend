package my.hive_back.api.notification;

import jakarta.annotation.Resource;
import my.hive.common.dto.Result;
import my.hive_back.module.notification.model.vo.AnnouncementVO;
import my.hive_back.module.notification.service.NotificationAnnouncementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序通知公告接口。
 */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    @Resource
    private NotificationAnnouncementService notificationAnnouncementService;

    @GetMapping("/announcements")
    public Result<List<AnnouncementVO>> announcements(@RequestParam(required = false) Integer limit) {
        return Result.success(notificationAnnouncementService.announcements(limit));
    }
}

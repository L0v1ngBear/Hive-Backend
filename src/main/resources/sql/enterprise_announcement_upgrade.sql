CREATE TABLE IF NOT EXISTS `enterprise_announcement` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_code` varchar(50) NOT NULL COMMENT '租户编码',
  `announcement_code` varchar(120) NOT NULL COMMENT '公告编号',
  `title` varchar(120) NOT NULL COMMENT '公告标题',
  `content` varchar(1000) NOT NULL COMMENT '公告内容',
  `level` varchar(20) NOT NULL DEFAULT 'normal' COMMENT '公告级别(normal/urgent/important)',
  `route` varchar(255) DEFAULT NULL COMMENT '前端跳转地址',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态(1有效，0失效)',
  `publisher_user_id` bigint DEFAULT NULL COMMENT '发布人用户ID',
  `publisher_name` varchar(80) DEFAULT NULL COMMENT '发布人姓名',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_enterprise_announcement_code` (`tenant_code`,`announcement_code`),
  KEY `idx_enterprise_announcement_recent` (`tenant_code`,`status`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='企业通知公告主表';

CREATE TABLE IF NOT EXISTS `enterprise_announcement_read` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_code` varchar(50) NOT NULL COMMENT '租户编码',
  `announcement_id` bigint NOT NULL COMMENT '公告ID',
  `announcement_code` varchar(120) NOT NULL COMMENT '公告编号',
  `user_id` bigint NOT NULL COMMENT '员工用户ID',
  `user_name` varchar(80) DEFAULT NULL COMMENT '员工姓名',
  `department_name` varchar(120) DEFAULT NULL COMMENT '部门名称',
  `position_name` varchar(120) DEFAULT NULL COMMENT '职位名称',
  `read_flag` tinyint NOT NULL DEFAULT '0' COMMENT '是否已读',
  `read_time` datetime DEFAULT NULL COMMENT '已读时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_enterprise_announcement_read_user` (`tenant_code`,`announcement_id`,`user_id`),
  KEY `idx_enterprise_announcement_read_code` (`tenant_code`,`announcement_code`,`user_id`),
  KEY `idx_enterprise_announcement_read_status` (`tenant_code`,`announcement_id`,`read_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='企业通知公告已读记录表';

INSERT INTO enterprise_announcement (
    tenant_code, announcement_code, title, content, level, route, status, create_time, update_time
)
SELECT
    parent.tenant_code,
    COALESCE(NULLIF(parent.biz_id, ''), NULLIF(parent.dedupe_key, ''), CONCAT('ANNOUNCEMENT:', parent.id)) AS announcement_code,
    parent.title,
    parent.content,
    CASE
      WHEN parent.level IN ('urgent', 'critical') THEN 'urgent'
      WHEN parent.level IN ('important', 'warning') THEN 'important'
      ELSE 'normal'
    END AS level,
    parent.route,
    parent.status,
    COALESCE(parent.create_time, NOW()),
    COALESCE(parent.update_time, parent.create_time, NOW())
FROM notification_record parent
WHERE parent.biz_type = 'ANNOUNCEMENT'
  AND parent.receiver_user_id IS NULL
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    content = VALUES(content),
    level = VALUES(level),
    route = VALUES(route),
    status = VALUES(status),
    update_time = VALUES(update_time);

INSERT INTO enterprise_announcement_read (
    tenant_code, announcement_id, announcement_code, user_id, user_name,
    department_name, position_name, read_flag, read_time, create_time, update_time
)
SELECT
    receiver.tenant_code,
    announcement.id,
    announcement.announcement_code,
    receiver.receiver_user_id,
    COALESCE(receiver.receiver_name, u.name),
    u.department_name,
    u.position,
    COALESCE(receiver.read_flag, 0),
    receiver.read_time,
    COALESCE(receiver.create_time, NOW()),
    COALESCE(receiver.update_time, receiver.create_time, NOW())
FROM notification_record parent
INNER JOIN enterprise_announcement announcement
  ON BINARY announcement.tenant_code = BINARY parent.tenant_code
 AND announcement.announcement_code = COALESCE(NULLIF(parent.biz_id, ''), NULLIF(parent.dedupe_key, ''), CONCAT('ANNOUNCEMENT:', parent.id))
INNER JOIN notification_record receiver
  ON BINARY receiver.tenant_code = BINARY parent.tenant_code
 AND receiver.biz_type = 'ANNOUNCEMENT'
 AND receiver.biz_id = parent.biz_id
 AND receiver.receiver_user_id IS NOT NULL
LEFT JOIN user u
  ON u.id = receiver.receiver_user_id
 AND BINARY u.tenant_code = BINARY receiver.tenant_code
WHERE parent.biz_type = 'ANNOUNCEMENT'
  AND parent.receiver_user_id IS NULL
ON DUPLICATE KEY UPDATE
    user_name = VALUES(user_name),
    department_name = VALUES(department_name),
    position_name = VALUES(position_name),
    read_time = CASE
      WHEN enterprise_announcement_read.read_flag = 1 THEN enterprise_announcement_read.read_time
      WHEN VALUES(read_flag) = 1 THEN VALUES(read_time)
      ELSE enterprise_announcement_read.read_time
    END,
    read_flag = GREATEST(COALESCE(enterprise_announcement_read.read_flag, 0), COALESCE(VALUES(read_flag), 0)),
    update_time = VALUES(update_time);

UPDATE notification_record
SET status = 0,
    update_time = NOW()
WHERE biz_type = 'ANNOUNCEMENT';

CREATE TABLE IF NOT EXISTS `wechat_subscribe_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_code` varchar(50) NOT NULL COMMENT '租户编码',
  `user_id` bigint NOT NULL COMMENT '系统用户ID',
  `openid` varchar(128) NOT NULL COMMENT '微信小程序openid',
  `template_id` varchar(128) NOT NULL COMMENT '订阅消息模板ID',
  `subscribe_status` varchar(20) NOT NULL DEFAULT 'accept' COMMENT '订阅授权状态：accept/reject/ban',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_user_template` (`tenant_code`, `user_id`, `template_id`),
  KEY `idx_openid` (`openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='微信订阅消息授权用户表';

CREATE TABLE `tenant_database_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_code` varchar(64) NOT NULL COMMENT '租户编码',
  `datasource_key` varchar(64) NOT NULL COMMENT '数据源标识',
  `jdbc_url` varchar(500) NOT NULL COMMENT 'JDBC连接串',
  `jdbc_username` varchar(128) NOT NULL COMMENT '数据库用户名',
  `jdbc_password` varchar(255) NOT NULL COMMENT '数据库密码',
  `jdbc_driver_class` varchar(128) NOT NULL DEFAULT 'com.mysql.cj.jdbc.Driver' COMMENT '驱动类名',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_database_config_tenant` (`tenant_code`),
  UNIQUE KEY `uk_tenant_database_config_ds_key` (`datasource_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户数据库连接配置表';

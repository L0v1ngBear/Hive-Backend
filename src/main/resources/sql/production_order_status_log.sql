CREATE TABLE IF NOT EXISTS `production_order_status_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_code` varchar(64) NOT NULL COMMENT '租户编码',
  `order_id` varchar(64) NOT NULL COMMENT '生产订单号',
  `old_status` varchar(100) DEFAULT NULL COMMENT '变更前状态',
  `new_status` varchar(100) DEFAULT NULL COMMENT '变更后状态',
  `operate_type` varchar(32) DEFAULT NULL COMMENT '操作类型',
  `remark` varchar(255) DEFAULT NULL COMMENT '变更备注',
  `operator` varchar(64) DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(100) DEFAULT NULL COMMENT '操作人姓名',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_order_time` (`tenant_code`, `order_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产订单状态流转日志';

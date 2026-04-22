CREATE TABLE IF NOT EXISTS `sales_order_status_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_code` varchar(50) NOT NULL COMMENT '租户编码',
  `order_id` varchar(64) NOT NULL COMMENT '销售订单号',
  `old_status` varchar(64) DEFAULT NULL COMMENT '变更前状态',
  `new_status` varchar(64) DEFAULT NULL COMMENT '变更后状态',
  `operate_type` varchar(32) DEFAULT NULL COMMENT '操作类型：create-创建，status_change-状态变更，sync-关联同步，update-信息更新',
  `remark` varchar(500) DEFAULT NULL COMMENT '操作备注',
  `operator` varchar(64) DEFAULT NULL COMMENT '操作人',
  `operator_name` varchar(100) DEFAULT NULL COMMENT '操作人姓名',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_sales_order_status_log_tenant_order` (`tenant_code`, `order_id`),
  KEY `idx_sales_order_status_log_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售订单状态流转日志';

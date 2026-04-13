-- 生产订单状态更新日志表
CREATE TABLE IF NOT EXISTS production_order_status_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  tenant_code VARCHAR(64) NOT NULL COMMENT '租户编码',
  order_id VARCHAR(64) NOT NULL COMMENT '生产订单编号',
  old_status VARCHAR(100) DEFAULT NULL COMMENT '变更前状态',
  new_status VARCHAR(100) DEFAULT NULL COMMENT '变更后状态',
  operate_type VARCHAR(32) DEFAULT NULL COMMENT '操作类型',
  remark VARCHAR(255) DEFAULT NULL COMMENT '变更备注',
  operator VARCHAR(64) DEFAULT NULL COMMENT '操作人',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (id),
  KEY idx_tenant_order_time (tenant_code, order_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产订单状态更新日志表';
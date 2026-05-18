-- 财务审批表：用于报销、付款等财务审批流
CREATE TABLE IF NOT EXISTS finance_approval (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  approval_code VARCHAR(64) NOT NULL COMMENT '审批单号',
  tenant_code VARCHAR(64) NOT NULL COMMENT '租户编码',
  apply_user_id BIGINT NOT NULL COMMENT '申请人ID',
  category VARCHAR(64) NOT NULL COMMENT '财务类别',
  amount DECIMAL(12,2) NOT NULL COMMENT '申请金额',
  reason VARCHAR(500) NOT NULL COMMENT '申请事由',
  attachment_name VARCHAR(180) DEFAULT NULL COMMENT '附件名称',
  attachment_url VARCHAR(512) DEFAULT NULL COMMENT '附件地址',
  attachment_size BIGINT DEFAULT NULL COMMENT '附件大小，字节',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-待审批，2-已通过，3-已拒绝',
  auditor_id BIGINT DEFAULT NULL COMMENT '当前审批人ID',
  audit_comment VARCHAR(500) DEFAULT NULL COMMENT '审批意见',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_approval_code (tenant_code, approval_code),
  KEY idx_tenant_status_auditor (tenant_code, status, auditor_id),
  KEY idx_tenant_apply_user (tenant_code, apply_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='财务审批表';

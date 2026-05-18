-- 标签模板表：管理端上传 PRN/TSPL 模板，小程序按租户拉取后蓝牙打印
CREATE TABLE IF NOT EXISTS label_template (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  tenant_code VARCHAR(64) NOT NULL COMMENT '租户编码',
  name VARCHAR(100) NOT NULL COMMENT '模板名称',
  print_type VARCHAR(32) NOT NULL DEFAULT 'label' COMMENT '打印类型：label-标签，triplicate-三联单',
  content LONGTEXT NOT NULL COMMENT 'PRN/TSPL/ESC-POS 原始模板内容',
  design_json JSON DEFAULT NULL COMMENT 'visual designer json',
  width_mm DECIMAL(10,2) NOT NULL DEFAULT 70.00 COMMENT 'label width mm',
  height_mm DECIMAL(10,2) NOT NULL DEFAULT 50.00 COMMENT 'label height mm',
  variables VARCHAR(500) DEFAULT NULL COMMENT '模板变量，逗号分隔',
  file_name VARCHAR(255) DEFAULT NULL COMMENT '原始文件名',
  file_size BIGINT DEFAULT NULL COMMENT '文件大小，单位字节',
  is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认：0-否，1-是',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-停用，1-启用',
  creator_id BIGINT DEFAULT NULL COMMENT '创建人ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常，1-已删除',
  PRIMARY KEY (id),
  KEY idx_tenant_type_status (tenant_code, print_type, status),
  KEY idx_tenant_default (tenant_code, print_type, is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签模板表';

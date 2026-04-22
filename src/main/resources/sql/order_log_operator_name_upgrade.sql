ALTER TABLE `sales_order_status_log`
  ADD COLUMN `operator_name` varchar(100) DEFAULT NULL COMMENT '操作人姓名' AFTER `operator`;

ALTER TABLE `production_order_status_log`
  ADD COLUMN `operator_name` varchar(100) DEFAULT NULL COMMENT '操作人姓名' AFTER `operator`;

UPDATE `sales_order_status_log` l
LEFT JOIN `user` u
  ON u.id = CAST(IF(l.operator REGEXP '^[0-9]+$', l.operator, NULL) AS UNSIGNED)
 AND u.tenant_code = l.tenant_code
SET l.operator_name = COALESCE(u.name, l.operator)
WHERE l.operator_name IS NULL;

UPDATE `production_order_status_log` l
LEFT JOIN `user` u
  ON u.id = CAST(IF(l.operator REGEXP '^[0-9]+$', l.operator, NULL) AS UNSIGNED)
 AND u.tenant_code = l.tenant_code
SET l.operator_name = COALESCE(u.name, l.operator)
WHERE l.operator_name IS NULL;

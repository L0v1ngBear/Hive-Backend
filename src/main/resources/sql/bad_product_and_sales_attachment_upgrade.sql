-- 次品闭环字段与销售订单附件字段升级脚本。
-- 执行前请确认当前连接的是 hive 业务库；脚本可重复执行。

DROP PROCEDURE IF EXISTS add_column_if_absent;

DELIMITER //
CREATE PROCEDURE add_column_if_absent(
    IN table_name_param VARCHAR(64),
    IN column_name_param VARCHAR(64),
    IN ddl_sql_param TEXT
)
BEGIN
    SET @column_exists := (
        SELECT COUNT(1)
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = table_name_param
          AND COLUMN_NAME = column_name_param
    );

    SET @ddl := IF(@column_exists = 0, ddl_sql_param, CONCAT('SELECT ''', table_name_param, '.', column_name_param, ' already exists'''));
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
END//
DELIMITER ;

CALL add_column_if_absent(
    'bad_product_record',
    'responsible_person',
    'ALTER TABLE `bad_product_record` ADD COLUMN `responsible_person` VARCHAR(80) NULL COMMENT ''负责人员'' AFTER `description`'
);

CALL add_column_if_absent(
    'bad_product_record',
    'process_measure',
    'ALTER TABLE `bad_product_record` ADD COLUMN `process_measure` VARCHAR(500) NULL COMMENT ''处理措施'' AFTER `responsible_person`'
);

CALL add_column_if_absent(
    'bad_product_record',
    'improvement_plan',
    'ALTER TABLE `bad_product_record` ADD COLUMN `improvement_plan` VARCHAR(500) NULL COMMENT ''改进方案'' AFTER `process_measure`'
);

CALL add_column_if_absent(
    'sales_order',
    'attachment_name',
    'ALTER TABLE `sales_order` ADD COLUMN `attachment_name` VARCHAR(255) NULL COMMENT ''附件原始文件名'' AFTER `remark`'
);

CALL add_column_if_absent(
    'sales_order',
    'attachment_url',
    'ALTER TABLE `sales_order` ADD COLUMN `attachment_url` VARCHAR(500) NULL COMMENT ''附件访问地址'' AFTER `attachment_name`'
);

CALL add_column_if_absent(
    'sales_order',
    'attachment_size',
    'ALTER TABLE `sales_order` ADD COLUMN `attachment_size` BIGINT NULL COMMENT ''附件大小，单位字节'' AFTER `attachment_url`'
);

DROP PROCEDURE IF EXISTS add_column_if_absent;

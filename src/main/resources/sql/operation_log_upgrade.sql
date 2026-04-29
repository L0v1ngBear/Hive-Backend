-- 公共操作日志升级脚本：新建 operation_log，并兼容补齐 log_level 字段
SOURCE D:/HiveBackend/server/src/main/resources/sql/operation_log.sql;

SET @db_name = DATABASE();

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE operation_log ADD COLUMN log_level VARCHAR(16) NOT NULL DEFAULT ''INFO'' COMMENT ''日志级别：INFO-信息，WARN-警告，ERROR-错误'' AFTER description',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db_name
    AND TABLE_NAME = 'operation_log'
    AND COLUMN_NAME = 'log_level'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE INDEX idx_level_time ON operation_log(log_level, create_time)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db_name
    AND TABLE_NAME = 'operation_log'
    AND INDEX_NAME = 'idx_level_time'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

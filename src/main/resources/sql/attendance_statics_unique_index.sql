SET @index_count := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'attendance_statics'
      AND index_name = 'uk_user_tenant_date'
);

SET @sql := IF(
    @index_count = 0,
    'ALTER TABLE attendance_statics ADD UNIQUE INDEX uk_user_tenant_date (tenant_code, user_id, statistics_date)',
    'SELECT ''uk_user_tenant_date already exists'' AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

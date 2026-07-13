-- 手机号隐私保护字段升级脚本。
-- 执行范围：hive 数据库。
-- 说明：
-- 1. phone_hash 用于手机号登录、查重和精确搜索，不允许还原手机号。
-- 2. phone_mask 用于接口展示和 Excel 导出。
-- 3. 旧数据的 phone 明文字段先保留兼容，待使用同一 PRIVACY_HASH_SECRET 回填 phone_hash 后再清理。

ALTER TABLE `user`
    ADD COLUMN `phone_hash` VARCHAR(128) DEFAULT NULL COMMENT '手机号不可逆哈希',
    ADD COLUMN `phone_mask` VARCHAR(32) DEFAULT NULL COMMENT '手机号脱敏展示';

CREATE INDEX `idx_user_tenant_phone_hash` ON `user` (`tenant_code`, `phone_hash`);

UPDATE `user`
SET `phone_mask` = CASE
    WHEN `phone` IS NULL OR `phone` = '' THEN NULL
    WHEN LENGTH(REPLACE(REPLACE(REPLACE(`phone`, ' ', ''), '-', ''), '+86', '')) >= 11
        THEN CONCAT(
            LEFT(REPLACE(REPLACE(REPLACE(`phone`, ' ', ''), '-', ''), '+86', ''), 3),
            '****',
            RIGHT(REPLACE(REPLACE(REPLACE(`phone`, ' ', ''), '-', ''), '+86', ''), 4)
        )
    ELSE '****'
END
WHERE `phone_mask` IS NULL;

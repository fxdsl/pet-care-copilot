-- 第七周为既有用户表补充认证、角色和资料字段。
-- 历史账号没有密码来源，因此统一禁用，避免使用可预测默认密码。
ALTER TABLE app_user
    ADD COLUMN password_hash VARCHAR(100) NULL AFTER username,
    ADD COLUMN role VARCHAR(30) NOT NULL DEFAULT 'USER' AFTER display_name,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' AFTER role,
    ADD COLUMN avatar_url VARCHAR(1000) NULL AFTER status,
    ADD COLUMN bio VARCHAR(500) NULL AFTER avatar_url,
    ADD COLUMN region VARCHAR(100) NULL AFTER bio,
    ADD COLUMN last_login_at TIMESTAMP(6) NULL AFTER region;

UPDATE app_user
SET password_hash = '!MIGRATED_ACCOUNT_DISABLED!', status = 'DISABLED'
WHERE password_hash IS NULL;

ALTER TABLE app_user
    MODIFY COLUMN password_hash VARCHAR(100) NOT NULL,
    ADD CONSTRAINT chk_app_user_role
        CHECK (role IN ('USER', 'VERIFIED_SELLER', 'MODERATOR', 'ADMIN')),
    ADD CONSTRAINT chk_app_user_status
        CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED'));

-- 历史匿名档案保留但没有归属，不会被任何用户接口查询到；新档案必须由服务写入 user_id。
ALTER TABLE pet_profile
    ADD COLUMN user_id CHAR(36) NULL AFTER id,
    ADD KEY idx_pet_profile_user_created (user_id, created_at DESC),
    ADD CONSTRAINT fk_pet_profile_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE;

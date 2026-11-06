-- V2__auth_multi_branch_google_reset.sql
-- Phân hệ AUTH: multi-branch, đăng nhập Google, đặt lại mật khẩu, nhật ký quản trị

-- 1. users ---------------------------------------------------------------
ALTER TABLE users
    ALTER COLUMN password_hash DROP NOT NULL,
    ALTER COLUMN tier SET NOT NULL,
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN loyalty_points SET NOT NULL,
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN date_of_birth DATE,
    ADD COLUMN gender VARCHAR(20),
    ADD COLUMN last_login_at TIMESTAMPTZ,
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN locked_until TIMESTAMPTZ,
    ADD COLUMN password_changed_at TIMESTAMPTZ;

ALTER TABLE users
    ADD CONSTRAINT chk_users_email_lower CHECK (email = lower(email)),
    ADD CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE','LOCKED','SUSPENDED','DELETED')),
    ADD CONSTRAINT chk_users_tier CHECK (tier IN ('STAR','GOLD','DIAMOND')),
    ADD CONSTRAINT chk_users_gender CHECK (gender IS NULL OR gender IN ('MALE','FEMALE','OTHER')),
    ADD CONSTRAINT chk_users_points CHECK (loyalty_points >= 0);

CREATE INDEX idx_users_status ON users(status);

-- 2. roles: thêm ROLE_MANAGER ----------------------------------------------
INSERT INTO roles (code, name, description) VALUES
('ROLE_MANAGER', 'Quản lý chi nhánh', 'Quản lý nhân viên và vận hành trong chi nhánh được gán')
ON CONFLICT (code) DO NOTHING;

-- 3. refresh_tokens: phát hiện dùng lại token cũ ----------------------------
ALTER TABLE refresh_tokens
    ALTER COLUMN revoked SET NOT NULL,
    ALTER COLUMN created_at SET NOT NULL,
    ADD COLUMN family_id UUID NOT NULL DEFAULT uuid_generate_v4(),
    ADD COLUMN replaced_by_id BIGINT REFERENCES refresh_tokens(id) ON DELETE SET NULL,
    ADD COLUMN revoked_at TIMESTAMPTZ,
    ADD COLUMN ip_address VARCHAR(45);

CREATE INDEX idx_refresh_tokens_family  ON refresh_tokens(family_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens(expires_at);

-- 4. Đăng nhập bằng nhà cung cấp ngoài (Google) ------------------------------
CREATE TABLE user_identities (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(30) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    provider_email VARCHAR(150),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMPTZ,
    CONSTRAINT chk_identity_provider CHECK (provider IN ('GOOGLE')),
    CONSTRAINT uq_identity_subject UNIQUE (provider, provider_user_id),
    CONSTRAINT uq_identity_user_provider UNIQUE (user_id, provider)
);

-- 5. Token một lần: quên mật khẩu, xác thực email ---------------------------
CREATE TABLE user_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_type VARCHAR(30) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_user_tokens_type CHECK (token_type IN ('PASSWORD_RESET','EMAIL_VERIFICATION'))
);
CREATE INDEX idx_user_tokens_user_type ON user_tokens(user_id, token_type);

-- 6. Multi-branch: người dùng <-> chi nhánh ---------------------------------
CREATE TABLE user_branch_assignments (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    cinema_id BIGINT NOT NULL REFERENCES cinemas(id) ON DELETE CASCADE,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    assigned_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_branch UNIQUE (user_id, cinema_id)
);
CREATE UNIQUE INDEX uq_user_primary_branch ON user_branch_assignments(user_id) WHERE is_primary;
CREATE INDEX idx_uba_cinema ON user_branch_assignments(cinema_id);

-- Chuyển dữ liệu cũ rồi bỏ cột staff_profiles.cinema_id
INSERT INTO user_branch_assignments (user_id, cinema_id, is_primary)
SELECT user_id, cinema_id, TRUE FROM staff_profiles WHERE cinema_id IS NOT NULL;

ALTER TABLE staff_profiles
    DROP COLUMN cinema_id,
    ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN hired_at DATE,
    ADD COLUMN updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    ADD CONSTRAINT chk_staff_status CHECK (status IN ('ACTIVE','ON_LEAVE','RESIGNED'));

-- 7. Nhật ký thao tác quản trị -------------------------------------------------
CREATE TABLE user_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    actor_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    target_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(50) NOT NULL,
    details JSONB,
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_target ON user_audit_logs(target_user_id, created_at DESC);

-- ========================
-- ENUM types
-- ========================
CREATE TYPE user_role           AS ENUM ('ADMIN', 'USER');
CREATE TYPE social_provider     AS ENUM ('FACEBOOK', 'TWITTER');
CREATE TYPE post_status         AS ENUM ('ACTIVE', 'DELETED');
CREATE TYPE import_batch_status AS ENUM ('PENDING', 'PROCESSING', 'DONE', 'FAILED');

-- ========================
-- Trigger function for updated_at
-- ========================
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ========================
-- Table: users
-- ========================
CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    email      VARCHAR(255) NOT NULL UNIQUE,
    name       VARCHAR(255) NOT NULL,
    avatar_url TEXT,
    role       user_role NOT NULL DEFAULT 'ADMIN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ========================
-- Table: import_batches
-- ========================
CREATE TABLE import_batches (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    total_records   INT NOT NULL DEFAULT 0,
    success_records INT NOT NULL DEFAULT 0,
    failed_records  INT NOT NULL DEFAULT 0,
    status          import_batch_status NOT NULL DEFAULT 'PENDING',
    imported_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_import_batches_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- ========================
-- Table: posts
-- ========================
CREATE TABLE posts (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    platform         social_provider NOT NULL,
    platform_post_id VARCHAR(255) NOT NULL,
    title            VARCHAR(500),
    content          TEXT,
    post_url         TEXT,
    published_at     TIMESTAMPTZ,
    import_batch_id  BIGINT,
    status           post_status NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_posts_user         FOREIGN KEY (user_id)         REFERENCES users(id),
    CONSTRAINT fk_posts_import_batch FOREIGN KEY (import_batch_id) REFERENCES import_batches(id)
);

-- Unique chỉ áp cho bài ACTIVE: bài đã soft-delete không chặn việc re-import
-- cùng platform_post_id (unique toàn cục sẽ khoá vĩnh viễn key sau khi xoá mềm)
CREATE UNIQUE INDEX uk_platform_post_active
    ON posts (platform, platform_post_id) WHERE status = 'ACTIVE';

CREATE TRIGGER trg_posts_updated_at
    BEFORE UPDATE ON posts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ========================
-- Table: social_accounts
-- ========================
CREATE TABLE social_accounts (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    provider            social_provider NOT NULL,
    provider_account_id VARCHAR(255) NOT NULL,
    access_token        TEXT NOT NULL,
    refresh_token       TEXT,
    token_expires_at    TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_social_accounts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_provider_account     UNIQUE (provider, provider_account_id)
);

-- ========================
-- Table: social_metrics
-- ========================
CREATE TABLE social_metrics (
    id              BIGSERIAL PRIMARY KEY,
    post_id         BIGINT NOT NULL,
    likes_count     BIGINT NOT NULL DEFAULT 0,
    shares_count    BIGINT NOT NULL DEFAULT 0,
    comments_count  BIGINT NOT NULL DEFAULT 0,
    followers_count BIGINT NOT NULL DEFAULT 0,
    reach           BIGINT NOT NULL DEFAULT 0,
    impressions     BIGINT NOT NULL DEFAULT 0,
    crawled_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_metrics_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
);

-- ========================
-- Indexes
-- ========================
CREATE INDEX idx_metrics_post_crawled ON social_metrics (post_id, crawled_at DESC);
CREATE INDEX idx_posts_platform       ON posts (platform, status);
-- Listing chính của dashboard: WHERE status ORDER BY created_at DESC
CREATE INDEX idx_posts_status_created  ON posts (status, created_at DESC);
CREATE INDEX idx_import_batches_user  ON import_batches (user_id, imported_at DESC);
CREATE INDEX idx_social_accounts_user ON social_accounts (user_id, provider);

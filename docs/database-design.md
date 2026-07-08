# Database Design — Social Analytics Dashboard

## Overview

- **Database**: PostgreSQL
- **ORM**: Spring Data JPA (Hibernate)
- **Entities**: `User`, `Post`, `SocialMetric`, `SocialAccount`, `ImportBatch`

---

## Entity Relationship Diagram

```
┌─────────────────┐         ┌─────────────────────┐
│      users      │         │   social_accounts   │
├─────────────────┤         ├─────────────────────┤
│ id (PK)         │◄───┐    │ id (PK)             │
│ email           │    │    │ user_id (FK→users)  │
│ name            │    │    │ provider            │
│ avatar_url      │    └────│ provider_account_id │
│ role            │         │ access_token        │
│ created_at      │         │ refresh_token       │
│ updated_at      │         │ token_expires_at    │
└─────────────────┘         │ created_at          │
                            └─────────────────────┘
                                      
┌─────────────────┐         ┌─────────────────────┐
│      posts      │         │   social_metrics    │
├─────────────────┤         ├─────────────────────┤
│ id (PK)         │◄───┐    │ id (PK)             │
│ user_id (FK)    │    └────│ post_id (FK→posts)  │
│ platform        │         │ likes_count         │
│ platform_post_id│         │ shares_count        │
│ title           │         │ comments_count      │
│ content         │         │ followers_count     │
│ post_url        │         │ reach               │
│ published_at    │         │ impressions         │
│ import_batch_id │         │ crawled_at          │
│ status          │         │ created_at          │
│ created_at      │         └─────────────────────┘
│ updated_at      │
└─────────────────┘

┌──────────────────────┐
│    import_batches    │
├──────────────────────┤
│ id (PK)              │
│ user_id (FK→users)   │
│ file_name            │
│ total_records        │
│ success_records      │
│ failed_records       │
│ status               │
│ imported_at          │
└──────────────────────┘
```

---

## PostgreSQL Types & Shared Objects

Define ENUM types and the `updated_at` trigger function once, before creating tables.

```sql
-- ENUM types
CREATE TYPE user_role           AS ENUM ('ADMIN', 'USER');
CREATE TYPE social_provider     AS ENUM ('FACEBOOK', 'TWITTER');
CREATE TYPE post_status         AS ENUM ('ACTIVE', 'DELETED');
CREATE TYPE import_batch_status AS ENUM ('PENDING', 'PROCESSING', 'DONE', 'FAILED');

-- Shared trigger function for auto-updating updated_at
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

---

## Table Definitions

### `users`

| Column       | Type           | Constraints              | Description                    |
|-------------|----------------|--------------------------|--------------------------------|
| id          | BIGSERIAL      | PK                       | Primary key (auto-increment)   |
| email       | VARCHAR(255)   | UNIQUE, NOT NULL         | User email                     |
| name        | VARCHAR(255)   | NOT NULL                 | Display name                   |
| avatar_url  | TEXT           | NULLABLE                 | Profile picture URL            |
| role        | user_role      | NOT NULL, DEFAULT 'ADMIN'| ADMIN / USER                   |
| created_at  | TIMESTAMPTZ    | NOT NULL                 | Record creation time           |
| updated_at  | TIMESTAMPTZ    | NOT NULL                 | Last update time (via trigger) |

```sql
CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    avatar_url  TEXT,
    role        user_role NOT NULL DEFAULT 'ADMIN',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
```

---

### `social_accounts`

Stores OAuth2 tokens per provider per user.

| Column              | Type           | Constraints          | Description                      |
|--------------------|----------------|----------------------|----------------------------------|
| id                 | BIGSERIAL      | PK                   | Primary key                      |
| user_id            | BIGINT         | FK→users, NOT NULL   | Owner user                       |
| provider           | social_provider| NOT NULL             | FACEBOOK / TWITTER               |
| provider_account_id| VARCHAR(255)   | NOT NULL             | Platform-side user ID            |
| access_token       | TEXT           | NOT NULL             | OAuth2 access token              |
| refresh_token      | TEXT           | NULLABLE             | OAuth2 refresh token             |
| token_expires_at   | TIMESTAMPTZ    | NULLABLE             | Token expiry                     |
| created_at         | TIMESTAMPTZ    | NOT NULL             | Record creation time             |

```sql
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
```

---

### `posts`

Social media posts imported via Excel or crawled from platforms.

| Column           | Type           | Constraints          | Description                        |
|-----------------|----------------|----------------------|------------------------------------|
| id              | BIGSERIAL      | PK                   | Primary key                        |
| user_id         | BIGINT         | FK→users, NOT NULL   | Admin who imported                 |
| platform        | social_provider| NOT NULL             | FACEBOOK / TWITTER                 |
| platform_post_id| VARCHAR(255)   | NOT NULL             | Original post ID on the platform   |
| title           | VARCHAR(500)   | NULLABLE             | Post title/caption                 |
| content         | TEXT           | NULLABLE             | Post body content                  |
| post_url        | TEXT           | NULLABLE             | Direct link to the post            |
| published_at    | TIMESTAMPTZ    | NULLABLE             | When the post was published        |
| import_batch_id | BIGINT         | FK→import_batches    | Which import batch created this    |
| status          | post_status    | NOT NULL             | ACTIVE / DELETED                   |
| created_at      | TIMESTAMPTZ    | NOT NULL             | Record creation time               |
| updated_at      | TIMESTAMPTZ    | NOT NULL             | Last update time (via trigger)     |

```sql
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
    CONSTRAINT fk_posts_import_batch FOREIGN KEY (import_batch_id) REFERENCES import_batches(id),
    CONSTRAINT uk_platform_post      UNIQUE (platform, platform_post_id)
);

CREATE TRIGGER trg_posts_updated_at
    BEFORE UPDATE ON posts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
```

---

### `social_metrics`

Snapshot of engagement metrics per post, collected each crawl run.

| Column          | Type        | Constraints           | Description                       |
|----------------|-------------|----------------------|-----------------------------------|
| id             | BIGSERIAL   | PK                   | Primary key                       |
| post_id        | BIGINT      | FK→posts, NOT NULL   | Associated post                   |
| likes_count    | BIGINT      | DEFAULT 0            | Number of likes/reactions         |
| shares_count   | BIGINT      | DEFAULT 0            | Number of shares/retweets         |
| comments_count | BIGINT      | DEFAULT 0            | Number of comments/replies        |
| followers_count| BIGINT      | DEFAULT 0            | Page/account followers at crawl   |
| reach          | BIGINT      | DEFAULT 0            | Estimated reach                   |
| impressions    | BIGINT      | DEFAULT 0            | Total impressions                 |
| crawled_at     | TIMESTAMPTZ | NOT NULL             | When this snapshot was taken      |
| created_at     | TIMESTAMPTZ | NOT NULL             | Record creation time              |

```sql
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
```

---

### `import_batches`

Tracks each Excel file import operation.

| Column          | Type                | Constraints          | Description                          |
|----------------|---------------------|----------------------|--------------------------------------|
| id             | BIGSERIAL           | PK                   | Primary key                          |
| user_id        | BIGINT              | FK→users, NOT NULL   | Admin who triggered the import       |
| file_name      | VARCHAR(255)        | NOT NULL             | Original uploaded filename           |
| total_records  | INT                 | DEFAULT 0            | Total rows in the file               |
| success_records| INT                 | DEFAULT 0            | Successfully imported rows           |
| failed_records | INT                 | DEFAULT 0            | Failed/skipped rows                  |
| status         | import_batch_status | NOT NULL             | PENDING / PROCESSING / DONE / FAILED |
| imported_at    | TIMESTAMPTZ         | NOT NULL             | Import completion time               |

```sql
CREATE TABLE import_batches (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    file_name        VARCHAR(255) NOT NULL,
    total_records    INT NOT NULL DEFAULT 0,
    success_records  INT NOT NULL DEFAULT 0,
    failed_records   INT NOT NULL DEFAULT 0,
    status           import_batch_status NOT NULL DEFAULT 'PENDING',
    imported_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_import_batches_user FOREIGN KEY (user_id) REFERENCES users(id)
);
```

---

## Indexes

```sql
-- Fast lookup of latest metrics per post
CREATE INDEX idx_metrics_post_crawled ON social_metrics (post_id, crawled_at DESC);

-- Dashboard queries by platform
CREATE INDEX idx_posts_platform       ON posts (platform, status);

-- Import history per user
CREATE INDEX idx_import_batches_user  ON import_batches (user_id, imported_at DESC);

-- Social account lookup by user
CREATE INDEX idx_social_accounts_user ON social_accounts (user_id, provider);
```

---

## JPA Entity Summary

| Entity          | Table             | Key Relationships                              |
|----------------|-------------------|------------------------------------------------|
| `User`          | `users`           | OneToMany → SocialAccount, Post, ImportBatch   |
| `SocialAccount` | `social_accounts` | ManyToOne → User                               |
| `Post`          | `posts`           | ManyToOne → User, ImportBatch; OneToMany → SocialMetric |
| `SocialMetric`  | `social_metrics`  | ManyToOne → Post                               |
| `ImportBatch`   | `import_batches`  | ManyToOne → User; OneToMany → Post             |

---

## Notes

- `social_metrics` stores **snapshots** (one row per crawl per post), not upserts — enables historical trend charts.
- `access_token` / `refresh_token` should be encrypted at rest (e.g., Jasypt or AES column encryption) before production.
- `import_batches.status` drives the JMS flow: `PENDING → PROCESSING → DONE/FAILED`.
- `updated_at` is managed by the `set_updated_at()` trigger — no `ON UPDATE` clause needed.
- Use `TIMESTAMPTZ` (timestamp with time zone) throughout; configure `spring.jpa.properties.hibernate.jdbc.time_zone=UTC` in `application.properties`.

# Entities

## Domain Model

### User
**Table:** `users`
**Source:** `entity/User.java`

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | BIGSERIAL | PK | |
| email | VARCHAR(255) | NOT NULL UNIQUE | OAuth2 email; Twitter may be absent |
| name | VARCHAR(255) | NOT NULL | Display name from provider |
| avatar_url | TEXT | nullable | Provider profile picture URL |
| role | UserRole | NOT NULL DEFAULT 'ADMIN' | ADMIN or USER |
| created_at | TIMESTAMPTZ | NOT NULL | @CreatedDate (JPA auditing) |
| updated_at | TIMESTAMPTZ | NOT NULL | @LastModifiedDate (JPA auditing) |

**Relationships:** One-to-many → Post, ImportBatch, SocialAccount

---

### Post
**Table:** `posts`
**Source:** `entity/Post.java`

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | BIGSERIAL | PK | |
| user_id | BIGINT | FK → users.id | Owner (imported by) |
| platform | SocialProvider | NOT NULL | FACEBOOK or TWITTER |
| platform_post_id | VARCHAR(255) | NOT NULL | Native ID on platform |
| title | VARCHAR(500) | nullable | Optional post title |
| content | TEXT | nullable | Post body text |
| post_url | TEXT | nullable | Link to original post |
| published_at | TIMESTAMPTZ | nullable | Publication time on platform |
| import_batch_id | BIGINT | FK → import_batches.id nullable | Batch that created this post |
| status | PostStatus | NOT NULL DEFAULT 'ACTIVE' | ACTIVE or DELETED (soft-delete) |
| created_at | TIMESTAMPTZ | NOT NULL | @CreatedDate |
| updated_at | TIMESTAMPTZ | NOT NULL | @LastModifiedDate |

**Unique index:** `uk_platform_post_active` on (platform, platform_post_id) WHERE status = 'ACTIVE' — soft-delete allows re-import of same key.

**Relationships:** Many-to-one → User, ImportBatch; One-to-many → SocialMetric

---

### SocialMetric
**Table:** `social_metrics`
**Source:** `entity/SocialMetric.java`

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | BIGSERIAL | PK | |
| post_id | BIGINT | FK → posts.id ON DELETE CASCADE | |
| likes_count | BIGINT | NOT NULL DEFAULT 0 | |
| shares_count | BIGINT | NOT NULL DEFAULT 0 | |
| comments_count | BIGINT | NOT NULL DEFAULT 0 | |
| followers_count | BIGINT | NOT NULL DEFAULT 0 | |
| reach | BIGINT | NOT NULL DEFAULT 0 | |
| impressions | BIGINT | NOT NULL DEFAULT 0 | |
| crawled_at | TIMESTAMPTZ | NOT NULL | Time of metric snapshot |
| created_at | TIMESTAMPTZ | NOT NULL | |

**Relationships:** Many-to-one → Post

---

### ImportBatch
**Table:** `import_batches`
**Source:** `entity/ImportBatch.java`

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | BIGSERIAL | PK | |
| user_id | BIGINT | FK → users.id | User who uploaded the file |
| file_name | VARCHAR(255) | NOT NULL | Original .xlsx filename |
| total_records | INT | NOT NULL DEFAULT 0 | Rows in the file |
| success_records | INT | NOT NULL DEFAULT 0 | Rows persisted |
| failed_records | INT | NOT NULL DEFAULT 0 | Rows rejected |
| status | ImportBatchStatus | NOT NULL DEFAULT 'PENDING' | PENDING → PROCESSING → DONE/FAILED |
| imported_at | TIMESTAMPTZ | NOT NULL | Batch creation timestamp |

**Relationships:** Many-to-one → User; One-to-many → Post

---

### SocialAccount
**Table:** `social_accounts`
**Source:** `entity/SocialAccount.java`

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | BIGSERIAL | PK | |
| user_id | BIGINT | FK → users.id ON DELETE CASCADE | |
| provider | SocialProvider | NOT NULL | FACEBOOK or TWITTER |
| provider_account_id | VARCHAR(255) | NOT NULL | Native account ID on platform |
| access_token | TEXT | NOT NULL | OAuth2 access token |
| refresh_token | TEXT | nullable | |
| token_expires_at | TIMESTAMPTZ | nullable | |
| created_at | TIMESTAMPTZ | NOT NULL | |

**Unique constraint:** `uk_provider_account` on (provider, provider_account_id)

**Relationships:** Many-to-one → User

---

## Enumerations

| Enum | Values | Source |
|------|--------|--------|
| SocialProvider | FACEBOOK, TWITTER | `entity/SocialProvider.java` |
| PostStatus | ACTIVE, DELETED | `entity/PostStatus.java` |
| ImportBatchStatus | PENDING, PROCESSING, DONE, FAILED | `entity/ImportBatchStatus.java` |
| UserRole | ADMIN, USER | `entity/UserRole.java` |

---

## JMS Value Objects (non-persisted)

### ImportCompletedMessage
**Source:** `messaging/ImportCompletedMessage.java`
**Transport:** JMS queue `IMPORT_COMPLETED` (JSON/TextMessage via MappingJackson2MessageConverter)

| Field | Type | Notes |
|-------|------|-------|
| batchId | Long | ID of the completed ImportBatch |
| recordCount | int | Number of successfully imported records |

### ImportSucceededEvent
**Source:** `messaging/ImportSucceededEvent.java`
**Transport:** Spring ApplicationEvent (in-process; triggers JMS publish after DB commit)

| Field | Type | Notes |
|-------|------|-------|
| batchId | Long | |
| recordCount | int | |

### ImportStats (ImportStatsCache.ImportStats)
**Source:** `messaging/ImportStatsCache.java`
**Transport:** In-memory only (AtomicReference)

| Field | Type | Notes |
|-------|------|-------|
| totalPosts | long | Total post count (all platforms) |
| perPlatform | Map<String, Long> | Key: SocialProvider.name() → count of ACTIVE posts |

---

## Entity Relationship Diagram

```
User ─────────── ImportBatch
  │                   │
  │               (batch_id)
  │                   │
  └──────────── Post ──┘
                  │
              SocialMetric (time-series, cascade delete)

User ──── SocialAccount (OAuth2 tokens, cascade delete)
```

---
status: draft
authored_by: takumi
created: 2026-07-15
lang: en
---

# Business Context — D4 Automated Crawl Job

## Why this feature

Social metric data (likes, shares, comments) goes stale quickly. Without an automated refresh mechanism, analysts must trigger imports manually. D4 introduces a background scheduler that keeps `SocialMetric` rows current without any human action.

## Stakeholder value

- **Analyst / Dashboard viewer:** sees "Last Updated" timestamp; knows data is fresh.
- **System:** eliminates the need for manual re-imports for routine metric refresh.

## Scope boundaries

- **In scope:** hourly scheduled job, thread-pool execution, mock API calls, "Last Updated" UI, last-crawl REST endpoint.
- **Out of scope:** real Facebook / Twitter API calls (deferred), persistent `lastCrawledAt` across restarts (in-memory only), per-post crawl status tracking.

## Business rules

| ID | Rule |
|----|------|
| BR-D4-01 | Only `PostStatus.ACTIVE` posts are crawled — deleted posts are skipped. |
| BR-D4-02 | A crawl failure for one post must not prevent crawling of others. |
| BR-D4-03 | `lastCrawledAt` reflects the job's completion wall-clock time, not individual post crawl time. |

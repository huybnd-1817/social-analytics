---
fcode: F007
status: draft
authored_by: takumi
created: 2026-07-21
lang: en
---

# F007_JmsImportPipeline — Business Context

**Priority**: P1
**Type**: background

## Problem

`ExcelImportService` handles import synchronously: parse → validate → persist, all in one request.
Post-import aggregation (total posts, per-platform counts) happens inline, tightly coupling the
import flow to downstream computation.

## Solution

After a successful import, publish an `ImportCompletedMessage` to the `IMPORT_COMPLETED` JMS queue.
A separate `ImportEventListener` consumes it asynchronously and recalculates aggregated stats.
This decouples import latency from aggregation work and allows future consumers to be added without
changing the import path.

## Scope

- Produce: after `importPosts()` DB transaction commits
- Consume: recalculate `totalPosts` and per-platform counts; cache in-memory
- DLQ: failed messages routed to `DLQ.IMPORT_COMPLETED` after exhausted retries
- Test: publish → consume end-to-end verified with embedded broker

## Out of Scope

- Persistent stats table (in-memory cache is sufficient for D5)
- Retry policy beyond Artemis default (6 attempts)
- WebSocket broadcast triggered by JMS (D5-07–D5-10)

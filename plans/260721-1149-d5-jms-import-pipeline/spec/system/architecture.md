---
status: draft
authored_by: takumi
created: 2026-07-21
lang: en
---

# System Architecture — JMS Messaging Layer (D5)

## Overview

D5 adds an asynchronous JMS messaging layer on top of the existing Spring Boot layered architecture.
The broker is embedded ActiveMQ Artemis (no external server). Messages flow from the import service
to a listener via a durable queue, decoupling import latency from downstream aggregation.

## Architecture After D5 (JMS additions)

```
HTTP Request
    │
    ▼
Spring Security Filter Chain
    │
    ▼
Controller layer  (ImportController, PostController, MetricController, …)
    │
    ▼
Service layer     (ExcelImportService, PostService, …)
    │  ① publishEvent(ImportSucceededEvent)
    ▼
Spring ApplicationEventPublisher
    │  ② @TransactionalEventListener(AFTER_COMMIT)
    ▼
ImportEventProducer
    │  ③ jmsTemplate.convertAndSend("IMPORT_COMPLETED", message)
    ▼
┌─────────────────────────────────┐
│  ActiveMQ Artemis (embedded)    │
│                                 │
│  Queue: IMPORT_COMPLETED        │
│  DLQ:   DLQ.IMPORT_COMPLETED    │
└────────────┬────────────────────┘
             │  ④ @JmsListener
             ▼
ImportEventListener
    │  ⑤ recalculate stats
    ▼
ImportStatsCache (in-memory AtomicReference)
    │
    ▼
Repository layer  (PostRepository — count queries)
    │
    ▼
Database (PostgreSQL)
```

## Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Broker | Embedded ActiveMQ Artemis | No external process; dev/test parity; Spring Boot auto-config |
| Publish timing | `@TransactionalEventListener(AFTER_COMMIT)` | Guarantees message only sent after DB commit; no phantom messages on rollback |
| Message format | JSON via `MappingJackson2MessageConverter` | Avoids Java serialization trusted-list issues; human-readable |
| Stats storage | In-memory `AtomicReference` | Sufficient for D5; no schema migration needed |
| DLQ | Artemis default DLQ (`DLQ.{queue}`) | Zero config; automatic after 6 failed deliveries |

## Integration Points

- `ExcelImportService.persistSuccess()` → publishes `ImportSucceededEvent` via `ApplicationEventPublisher`
- `ImportEventProducer` bridges Spring events to JMS (separation of concerns)
- `ImportEventListener` is the sole consumer; `ImportStatsCache` is its write target

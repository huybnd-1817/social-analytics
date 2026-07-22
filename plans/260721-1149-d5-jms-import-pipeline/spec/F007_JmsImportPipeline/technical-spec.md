---
fcode: F007
status: draft
authored_by: takumi
created: 2026-07-21
lang: en
---

# F007_JmsImportPipeline — Technical Spec

## Overview

JMS async pipeline triggered after a successful Excel import. Uses embedded ActiveMQ Artemis
(no external broker required) with Spring JMS. Message is sent only after the DB transaction
commits (via `@TransactionalEventListener(AFTER_COMMIT)`), guaranteeing at-most-once-per-commit
delivery semantics.

## Components

### Dependency (D5-01)
- `spring-boot-starter-artemis` — includes Artemis client + Spring JMS auto-config
- `artemis-jakarta-server` — embedded in-VM broker for dev/test
- Configure in `application.properties`:
  ```
  spring.artemis.mode=embedded
  spring.artemis.embedded.enabled=true
  spring.artemis.embedded.queues=IMPORT_COMPLETED
  ```

### Queue Constant (D5-02)
```java
// JmsQueues.java  (package config or messaging)
public final class JmsQueues {
    public static final String IMPORT_COMPLETED = "IMPORT_COMPLETED";
}
```

### Message Contract
```java
// ImportCompletedMessage.java  (package messaging)
public record ImportCompletedMessage(Long batchId, int recordCount) implements Serializable {}
```
Uses Jackson `MappingJackson2MessageConverter` (TextMessage/JSON) — no raw Java serialization.

### ImportEventProducer (D5-03)
- `@Component`
- Listens for internal `ImportSucceededEvent` via `@TransactionalEventListener(phase = AFTER_COMMIT)`
- Calls `jmsTemplate.convertAndSend(JmsQueues.IMPORT_COMPLETED, message)`
- If JMS send fails → log error, do NOT rethrow (import already committed)

### ImportSucceededEvent (internal Spring event)
```java
public record ImportSucceededEvent(Long batchId, int recordCount) {}
```
Published in `ExcelImportService.persistSuccess()` via `ApplicationEventPublisher.publishEvent()`.

### ImportEventListener (D5-04)
- `@Component`
- `@JmsListener(destination = JmsQueues.IMPORT_COMPLETED)`
- Queries `PostRepository.count()` and `PostRepository.countByPlatform()` per provider
- Stores result in `ImportStatsCache` (in-memory `@Component` with `AtomicReference`)
- Exposes `GET /import-stats` (optional) or used internally

### DLQ (D5-05)
Artemis embedded DLQ is automatic: after `redelivery-count` (default 6) exhausted, message
moves to queue `DLQ.IMPORT_COMPLETED`. No custom `broker.xml` needed for defaults.
Verification: configure listener to throw on a test message; assert DLQ has the message.

### Message Converter
Register `MappingJackson2MessageConverter` bean with `typeIdPropertyName` set — ensures JSON
round-trip without Java serialization trusted-list issues.

## Cross-Cutting

| Code | Rule | Verifiable |
|------|------|-----------|
| BR-010 | Event published only on AFTER_COMMIT — never on rollback | Integration test with rollback scenario |
| BR-011 | Listener failure must NOT affect the HTTP response (import already committed) | Test: listener throws, assert HTTP 200 |
| BR-012 | DLQ receives message after listener exhausts retries | Test: mock listener throw, verify DLQ |

## Files to Create

| File | Purpose |
|------|---------|
| `config/JmsConfig.java` | MessageConverter bean, JmsListenerContainerFactory with DLQ |
| `messaging/JmsQueues.java` | Queue name constants |
| `messaging/ImportCompletedMessage.java` | Message record |
| `messaging/ImportSucceededEvent.java` | Internal Spring event |
| `messaging/ImportEventProducer.java` | Transactional event → JMS send |
| `messaging/ImportEventListener.java` | JMS consumer + stats recalc |
| `messaging/ImportStatsCache.java` | In-memory aggregated stats holder |

## Files to Modify

| File | Change |
|------|--------|
| `pom.xml` | Add `spring-boot-starter-artemis`, `artemis-jakarta-server` (test scope) |
| `application.properties` | Add Artemis embedded config |
| `service/ExcelImportService.java` | Inject `ApplicationEventPublisher`; call `publishEvent` in `persistSuccess` |

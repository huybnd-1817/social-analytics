---
spec_promoted: docs/features/F007_JmsImportPipeline/
status: completed
created: 2026-07-21
completed: 2026-07-21
---

# Plan — D5 JMS Import Pipeline (F007)

## Overview

Implement tasks D5-01 to D5-06: async JMS pipeline triggered after Excel import success.
Embedded ActiveMQ Artemis; publish on AFTER_COMMIT; DLQ after 6 retries; integration test.

## Phases

| Phase | Title | Status |
|-------|-------|--------|
| [phase-01](phase-01-add-jms-dependency.md) | Add JMS dependency & config | [x] |
| [phase-02](phase-02-message-contract.md) | Message contract + queue constant | [x] |
| [phase-03](phase-03-import-event-producer.md) | ImportEventProducer + ExcelImportService wiring | [x] |
| [phase-04](phase-04-import-event-listener.md) | ImportEventListener + ImportStatsCache | [x] |
| [phase-05](phase-05-dlq-config.md) | JMS config bean + DLQ setup | [x] |
| [phase-06](phase-06-jms-tests.md) | Integration test: publish → consume | [x] |

## Key Dependencies

- phase-02 depends on phase-01 (dependency must be on classpath before writing message classes)
- phase-03 depends on phase-02 (uses `ImportCompletedMessage`, `JmsQueues`)
- phase-04 depends on phase-02 (uses same queue constant + message type)
- phase-05 must precede phase-06 (DLQ config tested in integration test)
- phase-06 depends on all prior phases

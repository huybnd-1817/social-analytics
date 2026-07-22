# Phase 01 — Add JMS Dependency & Config (D5-01)

**Status:** [x] completed

## Goal
Add ActiveMQ Artemis to pom.xml; configure embedded broker in application.properties.

## Files to Modify
- `pom.xml`
- `src/main/resources/application.properties`
- `src/main/resources/application-dev.properties`

## Steps

1. Add to `pom.xml` dependencies:
   ```xml
   <!-- JMS: ActiveMQ Artemis embedded broker + Spring JMS -->
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-artemis</artifactId>
   </dependency>
   <dependency>
       <groupId>org.apache.activemq</groupId>
       <artifactId>artemis-jakarta-server</artifactId>
       <scope>runtime</scope>
   </dependency>
   ```

2. Add to `application.properties`:
   ```properties
   # ========================
   # D5: JMS — ActiveMQ Artemis (embedded)
   # ========================
   spring.artemis.mode=embedded
   spring.artemis.embedded.enabled=true
   spring.artemis.embedded.queues=IMPORT_COMPLETED
   ```

3. Compile check: `./mvnw compile -q`

## Success Criteria
- `./mvnw compile` passes with no errors
- `spring-boot-starter-artemis` resolves in dependency tree

## Implementation Summary

**Completed:** D5-01

**Files Modified:**
- `pom.xml` — added `spring-boot-starter-artemis` and `artemis-jakarta-server` dependencies
- `src/main/resources/application.properties` — added embedded Artemis config (mode=embedded, queues=IMPORT_COMPLETED)
- `src/main/resources/application-test.properties` — added Artemis config to test profile

**Key Details:**
- Embedded broker enabled with `spring.artemis.embedded.enabled=true`
- Queue name `IMPORT_COMPLETED` auto-created on broker startup
- Compile passes; no dependency conflicts

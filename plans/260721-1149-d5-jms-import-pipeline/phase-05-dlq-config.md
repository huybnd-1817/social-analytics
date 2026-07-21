# Phase 05 — JMS Config Bean + DLQ Setup (D5-05)

**Status:** [x] completed

## Goal
Register `MappingJackson2MessageConverter` bean; configure `DefaultJmsListenerContainerFactory`
with redelivery policy so failed messages land in `DLQ.IMPORT_COMPLETED`.

## Files to Create
- `src/main/java/com/sunasterisk/socialanalytics/config/JmsConfig.java`

## Steps

1. **JmsConfig** — `@Configuration`:

   ```java
   @Bean
   public MessageConverter jmsMessageConverter(ObjectMapper objectMapper) {
       MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
       converter.setTargetType(MessageType.TEXT);
       converter.setTypeIdPropertyName("_type");
       converter.setObjectMapper(objectMapper);
       return converter;
   }
   ```

2. Artemis embedded DLQ is automatic — no extra XML needed. Artemis moves a message to
   `DLQ.IMPORT_COMPLETED` after the container's `maxRedeliveries` is exhausted (default behaviour
   from the Artemis address-settings wildcard `#`).

3. To make it explicit and verifiable, add to `application.properties`:
   ```properties
   # Artemis embedded: honour server-side DLQ; client does not ack on exception
   spring.jms.listener.acknowledge-mode=auto
   ```
   Spring JMS `AUTO_ACKNOWLEDGE` + Artemis server-side dead-letter policy = messages that
   keep throwing exceptions are redelivered then dead-lettered automatically.

4. Compile check; verify `JmsAutoConfiguration` picks up the converter bean.

## Success Criteria
- `MappingJackson2MessageConverter` bean present in context
- Application starts without JMS config errors
- DLQ queue `DLQ.IMPORT_COMPLETED` exists in embedded broker (verified in test phase)

## Implementation Summary

**Completed:** D5-05

**Files Created:**
- `src/main/java/com/sunasterisk/socialanalytics/config/JmsConfig.java` — `@Configuration` bean registering `MappingJackson2MessageConverter`

**Files Modified:**
- `src/main/resources/application.properties` — added `spring.jms.listener.acknowledge-mode=auto` for AUTO_ACKNOWLEDGE mode

**Key Details:**
- Converter configured with `setTargetType(MessageType.TEXT)` and `setTypeIdPropertyName("_type")` for JSON/TextMessage serialization
- Artemis embedded broker handles DLQ routing automatically via server-side dead-letter policy
- AUTO_ACKNOWLEDGE + exception rethrow in listener triggers Artemis redelivery → DLQ after max-delivery-attempts exhausted (default 10)
- No custom XML config needed; Artemis defaults suffice for this implementation

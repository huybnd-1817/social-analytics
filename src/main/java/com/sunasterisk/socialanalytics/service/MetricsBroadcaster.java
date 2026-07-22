package com.sunasterisk.socialanalytics.service;

import com.sunasterisk.socialanalytics.dto.MetricsUpdatePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsBroadcaster {

    private static final String TOPIC = "/topic/metrics-update";

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcast(String event) {
        String ts = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        messagingTemplate.convertAndSend(TOPIC, new MetricsUpdatePayload(event, ts));
        log.debug("ws broadcast: event={}", event);
    }
}

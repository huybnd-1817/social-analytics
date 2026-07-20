package com.sunasterisk.socialanalytics.dto;

import java.time.Instant;

public record LastUpdatedResponse(Instant lastCrawledAt) {
}

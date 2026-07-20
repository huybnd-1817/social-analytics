package com.sunasterisk.socialanalytics.controller;

import com.sunasterisk.socialanalytics.dto.LastUpdatedResponse;
import com.sunasterisk.socialanalytics.dto.MetricResponse;
import com.sunasterisk.socialanalytics.service.CrawlJobService;
import com.sunasterisk.socialanalytics.service.MetricService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/metrics")
@RequiredArgsConstructor
@Tag(name = "Metrics", description = "Social metric endpoints")
public class MetricController {

    private final MetricService metricService;
    private final CrawlJobService crawlJobService;

    @GetMapping
    @Operation(summary = "List all metric snapshots (paginated)")
    public Page<MetricResponse> list(
            @ParameterObject
            @PageableDefault(size = 20, sort = "crawledAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return metricService.findAll(pageable);
    }

    @GetMapping("/last-updated")
    @Operation(summary = "Timestamp of the last completed crawl job")
    public LastUpdatedResponse lastUpdated() {
        return new LastUpdatedResponse(crawlJobService.getLastCrawledAt());
    }
}

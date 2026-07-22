package com.sunasterisk.socialanalytics.controller;

import com.sunasterisk.socialanalytics.dto.ChartDataResponse;
import com.sunasterisk.socialanalytics.service.ChartDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/chart-data")
@RequiredArgsConstructor
@Tag(name = "Charts", description = "Time-series chart data for dashboard")
public class ChartController {

    private final ChartDataService chartDataService;

    @GetMapping
    @Operation(summary = "Likes and shares time-series, optionally filtered by platform and date range")
    public ChartDataResponse chartData(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return chartDataService.getChartData(platform, from, to);
    }
}

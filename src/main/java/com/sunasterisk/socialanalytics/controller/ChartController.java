package com.sunasterisk.socialanalytics.controller;

import com.sunasterisk.socialanalytics.dto.ChartDataResponse;
import com.sunasterisk.socialanalytics.service.ChartDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;

@RestController
@RequestMapping("/chart-data")
@RequiredArgsConstructor
@Tag(name = "Charts", description = "Time-series chart data for dashboard")
public class ChartController {

    private final ChartDataService chartDataService;

    @GetMapping
    @Operation(summary = "Likes and shares time-series, optionally filtered by platform and date range")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Chart data returned"),
        @ApiResponse(responseCode = "400", description = "Invalid timezone identifier or date-time format")
    })
    public ChartDataResponse chartData(
            @Parameter(description = "Social platform filter (e.g. facebook, twitter). Omit for all platforms.")
            @RequestParam(required = false) String platform,
            @Parameter(description = "Start of date range (ISO-8601, e.g. 2026-01-01T00:00:00Z)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "End of date range (ISO-8601, e.g. 2026-12-31T23:59:59Z)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(description = "IANA timezone for date grouping (default: UTC)")
            @RequestParam(required = false, defaultValue = "UTC") String timezone) {
        ZoneId zone;
        try {
            zone = ZoneId.of(timezone);
        } catch (DateTimeException e) {
            String display = timezone.length() > 64 ? timezone.substring(0, 64) + "…" : timezone;
            throw new IllegalArgumentException("Invalid timezone: " + display);
        }
        return chartDataService.getChartData(platform, from, to, zone);
    }
}

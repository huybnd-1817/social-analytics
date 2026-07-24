package com.sunasterisk.socialanalytics.controller;

import com.sunasterisk.socialanalytics.dto.ExchangeRateResponse;
import com.sunasterisk.socialanalytics.soap.ExchangeRateWebServiceClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/exchange-rate")
@RequiredArgsConstructor
@Tag(name = "Exchange Rate", description = "SOAP-backed exchange rate to VND")
public class ExchangeRateController {

    private final ExchangeRateWebServiceClient exchangeRateClient;

    @GetMapping
    @Operation(summary = "Get VND exchange rate for a currency via SOAP WebService")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Exchange rate returned"),
        @ApiResponse(responseCode = "500", description = "SOAP call failed or upstream service error")
    })
    public ExchangeRateResponse getExchangeRate(
            @Parameter(description = "ISO 4217 currency code (e.g. USD, EUR, JPY)", example = "USD")
            @RequestParam(defaultValue = "USD") String currency) {
        String normalized = currency.toUpperCase();
        double rate = exchangeRateClient.getExchangeRate(normalized);
        return new ExchangeRateResponse(normalized, rate);
    }
}

package com.sunasterisk.socialanalytics.soap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Slf4j
@Endpoint // Đánh dấu class là SOAP endpoint, Spring-WS sẽ scan và đăng ký tự động
public class ExchangeRateEndpoint {

    // Khai báo method xử lý SOAP request có localPart="GetExchangeRateRequest" trong đúng namespace
    @PayloadRoot(namespace = SoapConstants.NAMESPACE_URI, localPart = "GetExchangeRateRequest")
    @ResponsePayload // Kết quả trả về sẽ được marshal thành SOAP response body
    public GetExchangeRateResponse getExchangeRate(@RequestPayload GetExchangeRateRequest request) {
        // Lấy mã tiền tệ từ request; mặc định là chuỗi rỗng nếu null
        String currency = request.getCurrency() != null ? request.getCurrency() : "";

        // Tra cứu tỷ giá VND tương ứng với mã tiền tệ; trả về 0.0 nếu không hỗ trợ
        double rate = switch (currency) {
            case "USD" -> 25_350.0;
            case "EUR" -> 27_500.0;
            case "JPY" -> 170.0;
            case "GBP" -> 32_000.0;
            default -> 0.0;
        };
        log.debug("SOAP exchange-rate: currency={} rate={}", currency, rate);

        // Đóng gói kết quả vào response object để Spring-WS marshal thành XML trả về client
        GetExchangeRateResponse response = new GetExchangeRateResponse();
        response.setCurrency(currency);
        response.setRate(rate);
        return response;
    }
}

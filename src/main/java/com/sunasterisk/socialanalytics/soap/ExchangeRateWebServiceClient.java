package com.sunasterisk.socialanalytics.soap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.ws.client.core.WebServiceTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateWebServiceClient {

    // WebServiceTemplate được inject từ WebServiceConfig, đã cấu hình sẵn JAXB marshaller và defaultUri
    private final WebServiceTemplate webServiceTemplate;

    /**
     * Gọi SOAP endpoint nội bộ để lấy tỷ giá VND theo mã tiền tệ.
     * Sử dụng defaultUri đã cấu hình sẵn trên bean WebServiceTemplate.
     */
    public double getExchangeRate(String currency) {
        GetExchangeRateRequest request = new GetExchangeRateRequest(); // Tạo SOAP request object
        request.setCurrency(currency);                                  // Gán mã tiền tệ cần tra cứu
        try {
            // Marshal request → XML, gửi HTTP POST đến SOAP endpoint, nhận XML response rồi unmarshal → Java object
            GetExchangeRateResponse response =
                    (GetExchangeRateResponse) webServiceTemplate.marshalSendAndReceive(request);
            if (response == null) {
                // SOAP endpoint trả về response rỗng — không xảy ra bình thường, throw để caller xử lý
                throw new RuntimeException("Empty SOAP response for currency: " + currency);
            }
            log.debug("Exchange rate received: currency={} rate={}", currency, response.getRate());
            return response.getRate(); // Trả về tỷ giá VND
        } catch (Exception e) {
            // Bắt mọi lỗi SOAP (network, parse, timeout...) và wrap thành RuntimeException
            log.error("SOAP call failed for currency={}: {}", currency, e.getMessage());
            throw new RuntimeException("Failed to fetch exchange rate for: " + currency, e);
        }
    }
}

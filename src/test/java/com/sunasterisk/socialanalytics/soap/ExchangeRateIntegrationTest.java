package com.sunasterisk.socialanalytics.soap;

import com.sunasterisk.socialanalytics.dto.ExchangeRateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;
import org.springframework.ws.client.core.WebServiceTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm thử end-to-end: REST → SOAP client → SOAP server (MessageDispatcherServlet) → ExchangeRateEndpoint.
 * Xác nhận toàn bộ luồng thực tế mà unit test (dùng mock WebServiceTemplate) không thể bao phủ.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // Khởi động server thật trên cổng ngẫu nhiên
@ActiveProfiles("test") // Dùng cấu hình profile "test" (application-test.properties)
class ExchangeRateIntegrationTest {

    @LocalServerPort
    private int port; // Cổng ngẫu nhiên được Spring Boot chọn khi khởi động test

    @Autowired
    private WebServiceTemplate webServiceTemplate; // Inject bean từ WebServiceConfig để gọi SOAP

    private RestTemplate restTemplate; // HTTP client dùng để gọi REST endpoint

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        // Ghi đè defaultUri để WebServiceTemplate gửi SOAP request đến đúng cổng test.
        // Lưu ý: WebServiceTemplate là singleton bean — mutation này ảnh hưởng suốt lifecycle của context.
        // Nếu thêm test class khác dùng RANDOM_PORT + @ActiveProfiles("test"), cần @DirtiesContext để tránh dùng chung context đã bị mutate.
        webServiceTemplate.setDefaultUri("http://localhost:" + port + "/ws/");
    }

    @Test
    void getExchangeRate_usd_returnsCorrectRate() {
        // Gọi REST endpoint với currency=USD, kiểm tra tỷ giá trả về đúng
        ResponseEntity<ExchangeRateResponse> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/exchange-rate?currency=USD",
                ExchangeRateResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().currency()).isEqualTo("USD");
        assertThat(response.getBody().rate()).isEqualTo(25_350.0); // Tỷ giá USD/VND cố định trong endpoint
    }

    @Test
    void getExchangeRate_lowercase_normalizedToUppercase() {
        // Gửi "eur" chữ thường, kiểm tra controller đã normalize lên "EUR" trước khi gọi SOAP
        ResponseEntity<ExchangeRateResponse> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/exchange-rate?currency=eur",
                ExchangeRateResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().currency()).isEqualTo("EUR"); // Phải là chữ hoa
        assertThat(response.getBody().rate()).isEqualTo(27_500.0);
    }

    @Test
    void getExchangeRate_defaultCurrency_returnsUsd() {
        // Không truyền currency, kiểm tra giá trị mặc định là USD
        ResponseEntity<ExchangeRateResponse> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/exchange-rate",
                ExchangeRateResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().currency()).isEqualTo("USD");
        assertThat(response.getBody().rate()).isEqualTo(25_350.0);
    }

    @Test
    void getExchangeRate_unknownCurrency_returnsZeroRate() {
        // Tiền tệ không được hỗ trợ phải trả về rate = 0.0 thay vì lỗi
        ResponseEntity<ExchangeRateResponse> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/exchange-rate?currency=XYZ",
                ExchangeRateResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().currency()).isEqualTo("XYZ");
        assertThat(response.getBody().rate()).isEqualTo(0.0); // default case trong ExchangeRateEndpoint
    }
}

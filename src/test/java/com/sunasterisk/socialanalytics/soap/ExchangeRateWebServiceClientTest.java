package com.sunasterisk.socialanalytics.soap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ws.client.core.WebServiceTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeRateWebServiceClientTest {

    @Mock
    private WebServiceTemplate webServiceTemplate;

    @InjectMocks
    private ExchangeRateWebServiceClient client;

    private static GetExchangeRateResponse buildResponse(String currency, double rate) {
        GetExchangeRateResponse r = new GetExchangeRateResponse();
        r.setCurrency(currency);
        r.setRate(rate);
        return r;
    }

    @Test
    void getExchangeRate_returnsRateFromSoapResponse() {
        when(webServiceTemplate.marshalSendAndReceive(any(GetExchangeRateRequest.class)))
                .thenReturn(buildResponse("USD", 25_350.0));

        double rate = client.getExchangeRate("USD");

        assertThat(rate).isEqualTo(25_350.0);
        ArgumentCaptor<GetExchangeRateRequest> captor =
                ArgumentCaptor.forClass(GetExchangeRateRequest.class);
        verify(webServiceTemplate).marshalSendAndReceive(captor.capture());
        assertThat(captor.getValue().getCurrency()).isEqualTo("USD");
    }

    @Test
    void getExchangeRate_differentCurrencies_passesCorrectRequest() {
        when(webServiceTemplate.marshalSendAndReceive(any(GetExchangeRateRequest.class)))
                .thenReturn(buildResponse("EUR", 27_500.0));

        double rate = client.getExchangeRate("EUR");

        assertThat(rate).isEqualTo(27_500.0);
        ArgumentCaptor<GetExchangeRateRequest> captor =
                ArgumentCaptor.forClass(GetExchangeRateRequest.class);
        verify(webServiceTemplate).marshalSendAndReceive(captor.capture());
        assertThat(captor.getValue().getCurrency()).isEqualTo("EUR");
    }

    @Test
    void getExchangeRate_nullResponse_wrapsInRuntimeException() {
        // null return simulates a SOAP endpoint that sends no response body
        when(webServiceTemplate.marshalSendAndReceive(any(GetExchangeRateRequest.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> client.getExchangeRate("USD"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to fetch exchange rate for: USD");
    }

    @Test
    void getExchangeRate_soapThrows_wrapsInRuntimeException() {
        when(webServiceTemplate.marshalSendAndReceive(any(GetExchangeRateRequest.class)))
                .thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> client.getExchangeRate("USD"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to fetch exchange rate for: USD");
    }
}

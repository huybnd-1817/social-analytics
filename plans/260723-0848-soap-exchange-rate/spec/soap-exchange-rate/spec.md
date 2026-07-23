---
name: soap-exchange-rate
description: SOAP WebService client for mock exchange rate — Spring-WS, JAXB, REST facade
metadata:
  type: feature
  tasks: D6-01 to D6-05
---

## Feature: Exchange Rate SOAP WebService

### Scope
Implement an in-app SOAP server + client that returns mock exchange rates (VND per currency unit), exposed via REST `GET /exchange-rate?currency=USD`.

### Namespace
`http://socialanalytics.sunasterisk.com/exchangerate`

### New Files
| File | Purpose |
|------|---------|
| `soap/GetExchangeRateRequest.java` | JAXB-annotated SOAP request |
| `soap/GetExchangeRateResponse.java` | JAXB-annotated SOAP response |
| `soap/ExchangeRateEndpoint.java` | Spring-WS server endpoint (mock rates) |
| `soap/ExchangeRateWebServiceClient.java` | WebServiceTemplate-based client |
| `config/WebServiceConfig.java` | MessageDispatcherServlet + WebServiceTemplate beans |
| `dto/ExchangeRateResponse.java` | REST response DTO |
| `controller/ExchangeRateController.java` | REST controller |
| `ExchangeRateWebServiceClientTest.java` | Unit test (mock WebServiceTemplate) |

### Modified Files
- `pom.xml` — add `spring-ws-core`
- `SecurityConfig.java` — permit `/ws/*`, add CSRF ignore for `/exchange-rate`
- `application.properties` — add `exchange-rate.soap.endpoint` property

### Mock Rate Table
| Currency | Rate (VND) |
|----------|-----------|
| USD | 25,350 |
| EUR | 27,500 |
| JPY | 170 |
| GBP | 32,000 |
| default | 0.0 |

package com.sunasterisk.socialanalytics.config;

import com.sunasterisk.socialanalytics.soap.SoapConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.config.annotation.WsConfigurerAdapter;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

// Đánh dấu class là Spring configuration và bật hỗ trợ Spring-WS
@Configuration
@EnableWs
public class WebServiceConfig extends WsConfigurerAdapter {

    // Đọc URL endpoint SOAP từ application.properties; mặc định là localhost nếu không cấu hình
    @Value("${exchange-rate.soap.endpoint:http://localhost:8080/ws/}")
    private String defaultEndpointUri;

    /**
     * Đăng ký MessageDispatcherServlet của Spring-WS tại /ws/*,
     * tách biệt với DispatcherServlet của Spring MVC.
     */
    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(
            ApplicationContext applicationContext) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet(); // Servlet chuyên xử lý SOAP message
        servlet.setApplicationContext(applicationContext);                  // Gắn Spring context để servlet có thể tìm các bean
        servlet.setTransformWsdlLocations(true);                           // Tự động chuyển đổi URL trong WSDL theo host thực tế
        return new ServletRegistrationBean<>(servlet, "/ws/*");            // Map servlet vào path /ws/*
    }

    /**
     * Quét package JAXB để tìm các class được sinh ra từ XSD
     * (có annotation @XmlRootElement) dùng cho marshal/unmarshal.
     */
    @Bean
    public Jaxb2Marshaller jaxb2Marshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();                              // Chuyển đổi giữa Java object và XML
        // JAXB sẽ scan package com.sunasterisk.socialanalytics.soap để tìm các class có annotation:
        // @XmlRootElement - đánh dấu class có thể là root element của XML
        // @XmlType - đánh dấu class là một XML type
        // @XmlAccessorType - cấu hình cách JAXB access fields/properties
        marshaller.setContextPath("com.sunasterisk.socialanalytics.soap");
        return marshaller;
    }

    // Bean dùng để gọi SOAP web service từ phía client (nếu ứng dụng đóng vai client)
    @Bean
    public WebServiceTemplate webServiceTemplate(Jaxb2Marshaller jaxb2Marshaller) {
        WebServiceTemplate template = new WebServiceTemplate();
        template.setMarshaller(jaxb2Marshaller);       // Dùng JAXB để chuyển Java object → XML khi gửi request
        template.setUnmarshaller(jaxb2Marshaller);     // Dùng JAXB để chuyển XML → Java object khi nhận response
        template.setDefaultUri(defaultEndpointUri);    // Địa chỉ SOAP endpoint mặc định sẽ gửi request đến
        return template;
    }

    /**
     * Tự động sinh WSDL từ XSD schema.
     * Tên bean "exchange-rate" → WSDL được expose tại GET /ws/exchange-rate.wsdl
     */
    @Bean(name = "exchange-rate")
    public DefaultWsdl11Definition defaultWsdl11Definition(XsdSchema exchangeRateSchema) {
        DefaultWsdl11Definition definition = new DefaultWsdl11Definition();
        definition.setPortTypeName("ExchangeRatePort");          // Tên PortType trong WSDL (nhóm các operation)
        definition.setLocationUri("/ws");                        // Base URI của SOAP service trong WSDL
        definition.setTargetNamespace(SoapConstants.NAMESPACE_URI); // Namespace XML dùng trong WSDL và SOAP message
        definition.setSchema(exchangeRateSchema);                // Schema XSD định nghĩa cấu trúc request/response
        return definition;
    }

    // Load file XSD từ classpath để dùng làm schema cho WSDL
    @Bean
    public XsdSchema exchangeRateSchema() {
        return new SimpleXsdSchema(new ClassPathResource("xsd/exchange-rate.xsd"));
    }
}

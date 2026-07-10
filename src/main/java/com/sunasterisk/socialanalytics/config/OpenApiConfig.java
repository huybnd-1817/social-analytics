package com.sunasterisk.socialanalytics.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI socialAnalyticsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Social Analytics Dashboard API")
                        .description("Aggregates social engagement metrics (likes, shares, followers) "
                                + "from Facebook & Twitter into one dashboard.")
                        .version("v0.0.1")
                        .contact(new Contact().name("Sun Asterisk")));
    }
}

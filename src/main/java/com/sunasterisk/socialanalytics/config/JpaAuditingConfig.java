package com.sunasterisk.socialanalytics.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * @EnableJpaAuditing để ở config riêng, KHÔNG để trên class @SpringBootApplication:
 * slice test (@WebMvcTest) load main class làm config → auditing đòi JPA metamodel
 * mà slice web không có → context fail ("JPA metamodel must not be empty").
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}

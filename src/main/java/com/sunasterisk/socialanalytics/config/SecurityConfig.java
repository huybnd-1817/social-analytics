package com.sunasterisk.socialanalytics.config;

import com.sunasterisk.socialanalytics.security.CustomOAuth2UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           CustomOAuth2UserService oauth2UserService) throws Exception {
        http
            // Bắt đầu cấu hình authorization rules cho các request
            .authorizeHttpRequests(auth -> auth
                    // Khai báo các endpoint được phép truy cập public, không cần authentication
                    .requestMatchers(
                            "/login", "/login/**",          // Trang login và các sub-path (nếu có custom login flow)
                            "/oauth2/**", "/login/oauth2/**", // Endpoint OAuth2 authorization request & callback (do Spring Security tự expose)
                            "/swagger-ui/**", "/swagger-ui.html", // UI của Swagger để xem API docs
                            "/v3/api-docs/**",                // JSON spec của OpenAPI/Swagger
                            "/css/**", "/js/**", "/images/**", "/webjars/**", // Static resources (CSS, JS, ảnh, webjar libraries)
                            "/error",                         // Trang error mặc định của Spring Boot (tránh bị chặn login khi có exception)
                            "/ws/",                           // D6: SOAP POST endpoint (MessageDispatcherServlet root) — WebServiceTemplate gọi nội bộ
                            "/ws/exchange-rate.wsdl",         // D6: WSDL retrieval — không cần auth
                            "/exchange-rate"                  // D6: REST facade cho SOAP client
                    ).permitAll() // Cho phép tất cả các path trên truy cập mà không cần đăng nhập
                    .anyRequest().authenticated() // Mọi request còn lại đều bắt buộc phải authenticated
            )
            // Kích hoạt OAuth2 Login (login qua provider bên ngoài như Google, GitHub, Auth0...)
            .oauth2Login(oauth2 -> oauth2
                    .loginPage("/login") // Custom login page thay vì dùng trang login mặc định của Spring Security
                    .userInfoEndpoint(ui -> ui.userService(oauth2UserService)) // Custom service để xử lý/map thông tin user lấy từ provider (userinfo endpoint) sau khi login thành công
                    .successHandler((req, res, auth) -> res.sendRedirect("/")) // Xử lý khi login OAuth2 thành công -> redirect về trang chủ
                    .failureHandler((req, res, ex) -> {
                        log.error("OAuth2 login failed: {}", ex.getMessage(), ex);
                        res.sendRedirect("/login?error");
                    })
            )
            // Cấu hình logout
            .logout(logout -> logout
                    .logoutSuccessUrl("/login?logout") // Sau khi logout thành công, redirect về trang login kèm thông báo đã logout
                    .permitAll() // Cho phép mọi user (kể cả chưa login) truy cập endpoint logout
            );
        // CSRF: bật cho Thymeleaf forms (login, dashboard); tắt cho REST endpoints.
        // REST paths không có browser-form flow nên không có CSRF attack vector thực tế.
        // /posts/** — hiện chỉ có GET, nhưng exempt để sẵn sàng cho future write endpoints;
        // review lại khi thêm endpoint write có browser-form flow.
        http.csrf(csrf -> csrf
                .ignoringRequestMatchers(
                        "/import-posts",
                        "/posts/**",
                        "/metrics/**",
                        "/ws/**"  // D6: SOAP POST từ WebServiceTemplate không mang theo CSRF token
                )
        );
        return http.build();
    }
}

package com.sunasterisk.socialanalytics.controller;

import com.sunasterisk.socialanalytics.config.SecurityConfig;
import com.sunasterisk.socialanalytics.security.CustomOAuth2UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void dashboard_authenticated_facebook_showsNameEmailProvider() throws Exception {
        mockMvc.perform(get("/")
                .with(oauth2Login()
                        .attributes(attrs -> {
                            attrs.put("id", "fb-1");
                            attrs.put("name", "Alice");
                            attrs.put("email", "alice@example.com");
                        })))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("name", "Alice"))
                .andExpect(model().attribute("email", "alice@example.com"))
                .andExpect(model().attribute("provider", "test"));
    }

    @Test
    void dashboard_authenticated_twitter_showsNullEmail() throws Exception {
        mockMvc.perform(get("/")
                .with(oauth2Login()
                        .attributes(attrs -> {
                            attrs.put("id", "tw-1");
                            attrs.put("name", "Bob");
                            // Twitter không cung cấp email — email sẽ null
                        })))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("name", "Bob"))
                .andExpect(model().attribute("email", (Object) null))
                .andExpect(model().attribute("provider", "test"));
    }

    @Test
    void dashboard_authenticated_nameAttribute_null_showsUnknown() throws Exception {
        mockMvc.perform(get("/")
                .with(oauth2Login()
                        .attributes(attrs -> {
                            attrs.put("id", "g-1");
                            // name là null — controller fallback về "Unknown"
                            attrs.put("email", "user@gmail.com");
                        })))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("name", "Unknown"))
                .andExpect(model().attribute("email", "user@gmail.com"));
    }

    @Test
    void dashboard_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}

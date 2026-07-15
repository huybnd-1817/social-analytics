package com.sunasterisk.socialanalytics.config;

import com.sunasterisk.socialanalytics.controller.LoginController;
import com.sunasterisk.socialanalytics.security.CustomOAuth2UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoginController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void loginPage_isAccessible_withoutAuth() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    void loginPage_withErrorParam_isAccessible() throws Exception {
        mockMvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk());
    }

    @Test
    void loginPage_withLogoutParam_isAccessible() throws Exception {
        mockMvc.perform(get("/login").param("logout", ""))
                .andExpect(status().isOk());
    }

    @Test
    void protectedResource_unauthenticated_redirectsToLogin() throws Exception {
        // Bất kỳ URL nào nằm ngoài danh sách permitAll → Spring Security redirect về /login trước khi dispatch.
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void logout_withCsrf_redirectsToLoginWithLogoutParam() throws Exception {
        mockMvc.perform(post("/logout").with(user("testuser")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));
    }
}

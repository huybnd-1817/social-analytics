package com.sunasterisk.socialanalytics.security;

import com.sunasterisk.socialanalytics.entity.SocialAccount;
import com.sunasterisk.socialanalytics.entity.SocialProvider;
import com.sunasterisk.socialanalytics.entity.User;
import com.sunasterisk.socialanalytics.entity.UserRole;
import com.sunasterisk.socialanalytics.repository.SocialAccountRepository;
import com.sunasterisk.socialanalytics.repository.UserRepository;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@NullMarked
@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    // Raw OAuth2User stub được inject per-test thông qua anonymous subclass.
    private OAuth2User rawUser;

    private CustomOAuth2UserService service;

    @BeforeEach
    void setUp() {
        // Override fetchUserInfo() để không thực hiện HTTP call thật. Test set rawUser trước khi gọi loadUser().
        service = new CustomOAuth2UserService(userRepository, socialAccountRepository) {
            @Override
            protected OAuth2User fetchUserInfo(OAuth2UserRequest request) {
                return rawUser;
            }
        };
    }

    // ────────────────────────────── helpers ──────────────────────────────

    private OAuth2UserRequest facebookRequest() {
        return buildRequest("facebook", "id");
    }

    private OAuth2UserRequest twitterRequest() {
        return buildRequest("twitter", "data");
    }

    private OAuth2UserRequest buildRequest(String registrationId, String nameAttribute) {
        ClientRegistration reg = ClientRegistration.withRegistrationId(registrationId)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientId("test-client")
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://example.com/authorize")
                .tokenUri("https://example.com/token")
                .userInfoUri("https://example.com/userinfo")
                .userNameAttributeName(nameAttribute)
                .build();
        OAuth2AccessToken token = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "test-token",
                Instant.now(), Instant.now().plusSeconds(3600));
        return new OAuth2UserRequest(reg, token);
    }

    private DefaultOAuth2User facebookOAuth2User(String id, String name, String email) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", id);
        attrs.put("name", name);
        if (email != null) attrs.put("email", email);
        return new DefaultOAuth2User(Collections.emptyList(), attrs, "id");
    }

    private DefaultOAuth2User twitterOAuth2User(String id, String name) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("name", name);
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("data", data);
        return new DefaultOAuth2User(Collections.emptyList(), attrs, "data");
    }

    private User savedUser(Long id, String name, String email) {
        User u = new User();
        u.setId(id);
        u.setName(name);
        u.setEmail(email);
        u.setRole(UserRole.USER);
        return u;
    }

    // ────────────────────────────── Facebook ──────────────────────────────

    @Test
    void facebook_newUser_createsUserWithUserRole() {
        rawUser = facebookOAuth2User("fb-1", "Alice", "alice@example.com");
        User persisted = savedUser(1L, "Alice", "alice@example.com");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(persisted);
        when(socialAccountRepository.findByProviderAndProviderAccountId(SocialProvider.FACEBOOK, "fb-1"))
                .thenReturn(Optional.empty());
        when(socialAccountRepository.save(any(SocialAccount.class))).thenReturn(new SocialAccount());

        service.loadUser(facebookRequest());

        // BR-001: user mới phải được tạo với role USER, không phụ thuộc DB default ADMIN
        // Tạo captor cho kiểu User để "chụp" lại object User đó rồi assert từng field.
        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        assertThat(cap.getValue().getRole()).isEqualTo(UserRole.USER);
        assertThat(cap.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(cap.getValue().getName()).isEqualTo("Alice");
    }

    @Test
    void facebook_withBlankEmail_throwsOAuth2AuthenticationException() {
        rawUser = facebookOAuth2User("fb-blank", "Blank", "");

        assertThatThrownBy(() -> service.loadUser(facebookRequest()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .asInstanceOf(type(OAuth2AuthenticationException.class))
                .extracting(ex -> ex.getError().getErrorCode())
                .isEqualTo("email_required");

        verify(userRepository, never()).save(any());
        verify(socialAccountRepository, never()).save(any());
    }

    @Test
    void facebook_existingUserByEmail_isReused_notDuplicated() {
        rawUser = facebookOAuth2User("fb-2", "Bob", "bob@example.com");
        User existing = savedUser(5L, "Bob", "bob@example.com");

        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(existing));
        when(socialAccountRepository.findByProviderAndProviderAccountId(SocialProvider.FACEBOOK, "fb-2"))
                .thenReturn(Optional.empty());
        when(socialAccountRepository.save(any(SocialAccount.class))).thenReturn(new SocialAccount());

        service.loadUser(facebookRequest());

        // Không tạo User mới — dùng lại bản ghi đã có
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void facebook_withoutEmail_throwsOAuth2AuthenticationException() {
        // Quyết định #3: chặn đăng nhập khi Facebook không cung cấp email
        rawUser = facebookOAuth2User("fb-3", "Anon", null);

        assertThatThrownBy(() -> service.loadUser(facebookRequest()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .asInstanceOf(type(OAuth2AuthenticationException.class))
                .extracting(ex -> ex.getError().getErrorCode())
                .isEqualTo("email_required");

        verify(userRepository, never()).save(any());
        verify(socialAccountRepository, never()).save(any());
    }

    @Test
    void facebook_newSocialAccount_isCreatedWithAccessToken() {
        rawUser = facebookOAuth2User("fb-4", "Carol", "carol@example.com");
        User persisted = savedUser(2L, "Carol", "carol@example.com");

        when(userRepository.findByEmail("carol@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenReturn(persisted);
        when(socialAccountRepository.findByProviderAndProviderAccountId(SocialProvider.FACEBOOK, "fb-4"))
                .thenReturn(Optional.empty());
        when(socialAccountRepository.save(any(SocialAccount.class))).thenReturn(new SocialAccount());

        service.loadUser(facebookRequest());

        ArgumentCaptor<SocialAccount> cap = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountRepository).save(cap.capture());
        SocialAccount sa = cap.getValue();
        assertThat(sa.getProvider()).isEqualTo(SocialProvider.FACEBOOK);
        assertThat(sa.getProviderAccountId()).isEqualTo("fb-4");
        assertThat(sa.getAccessToken()).isEqualTo("test-token"); // BR-005: lưu plaintext trong phạm vi D3
    }

    @Test
    void facebook_existingSocialAccount_isUpserted() {
        rawUser = facebookOAuth2User("fb-5", "Dan", "dan@example.com");
        User existing = savedUser(3L, "Dan", "dan@example.com");
        SocialAccount existingSa = new SocialAccount();
        existingSa.setProvider(SocialProvider.FACEBOOK);
        existingSa.setProviderAccountId("fb-5");
        existingSa.setAccessToken("old-token");

        when(userRepository.findByEmail("dan@example.com")).thenReturn(Optional.of(existing));
        when(socialAccountRepository.findByProviderAndProviderAccountId(SocialProvider.FACEBOOK, "fb-5"))
                .thenReturn(Optional.of(existingSa));
        when(socialAccountRepository.save(any(SocialAccount.class))).thenReturn(existingSa);

        service.loadUser(facebookRequest());

        // Token được làm mới, cùng một bản ghi được cập nhật
        ArgumentCaptor<SocialAccount> cap = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountRepository).save(cap.capture());
        assertThat(cap.getValue().getAccessToken()).isEqualTo("test-token");
    }

    // ────────────────────────────── Twitter ──────────────────────────────

    @Test
    void twitter_happyPath_usesPlaceholderEmail() {
        // DEC-001: Twitter v2 trả về {"data": {"id":..., "name":...}} — không có trường email.
        // Placeholder ngăn vi phạm ràng buộc NOT NULL của User.email.
        rawUser = twitterOAuth2User("tw-1", "TweetUser");
        User persisted = savedUser(10L, "TweetUser", "twitter:tw-1@noemail.local");

        when(userRepository.findByEmail("twitter:tw-1@noemail.local")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(persisted);
        when(socialAccountRepository.findByProviderAndProviderAccountId(SocialProvider.TWITTER, "tw-1"))
                .thenReturn(Optional.empty());
        when(socialAccountRepository.save(any(SocialAccount.class))).thenReturn(new SocialAccount());

        service.loadUser(twitterRequest());

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        assertThat(cap.getValue().getEmail()).isEqualTo("twitter:tw-1@noemail.local");
        assertThat(cap.getValue().getRole()).isEqualTo(UserRole.USER); // BR-001
    }

    @Test
    void twitter_missingDataKey_throwsOAuth2AuthenticationException() {
        // Thiếu key "data" ở top level → service phải từ chối sạch sẽ
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("other_key", "value");
        rawUser = new DefaultOAuth2User(Collections.emptyList(), attrs, "other_key");

        assertThatThrownBy(() -> service.loadUser(twitterRequest()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .asInstanceOf(type(OAuth2AuthenticationException.class))
                .extracting(ex -> ex.getError().getErrorCode())
                .isEqualTo("twitter_missing_data");

        verify(userRepository, never()).save(any());
        verify(socialAccountRepository, never()).save(any());
    }

    @Test
    void twitter_dataPresent_butMissingId_throwsOAuth2AuthenticationException() {
        // H-1: data key tồn tại nhưng thiếu "id" → phải từ chối sạch sẽ, không NPE
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "No-ID User"); // "id" cố tình bị bỏ qua
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("data", data);
        rawUser = new DefaultOAuth2User(Collections.emptyList(), attrs, "data");

        assertThatThrownBy(() -> service.loadUser(twitterRequest()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .asInstanceOf(type(OAuth2AuthenticationException.class))
                .extracting(ex -> ex.getError().getErrorCode())
                .isEqualTo("twitter_missing_user_id");

        verify(userRepository, never()).save(any());
        verify(socialAccountRepository, never()).save(any());
    }

    @Test
    void twitter_returns_oauth2User_with_unwrapped_id_attribute() {
        rawUser = twitterOAuth2User("tw-2", "HandleUser");
        User persisted = savedUser(11L, "HandleUser", "twitter:tw-2@noemail.local");

        when(userRepository.findByEmail("twitter:tw-2@noemail.local")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenReturn(persisted);
        when(socialAccountRepository.findByProviderAndProviderAccountId(SocialProvider.TWITTER, "tw-2"))
                .thenReturn(Optional.empty());
        when(socialAccountRepository.save(any())).thenReturn(new SocialAccount());

        OAuth2User result = service.loadUser(twitterRequest());

        // User trả về có map data đã unwrap làm attributes, với "id" là name key
        assertThat(result.getName()).isEqualTo("tw-2");
        assertThat((String) result.getAttribute("id")).isEqualTo("tw-2");
        assertThat((String) result.getAttribute("name")).isEqualTo("HandleUser");
    }
}

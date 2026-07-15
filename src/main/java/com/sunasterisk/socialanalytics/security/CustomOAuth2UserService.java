package com.sunasterisk.socialanalytics.security;

import com.sunasterisk.socialanalytics.entity.SocialAccount;
import com.sunasterisk.socialanalytics.entity.SocialProvider;
import com.sunasterisk.socialanalytics.entity.User;
import com.sunasterisk.socialanalytics.entity.UserRole;
import com.sunasterisk.socialanalytics.repository.SocialAccountRepository;
import com.sunasterisk.socialanalytics.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@NullMarked
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;

    // Tách ra để tiện test — các test override phương thức này để không thực hiện HTTP call thật.
    protected OAuth2User fetchUserInfo(OAuth2UserRequest userRequest) {
        // Gọi implementation gốc của Spring (DefaultOAuth2UserService) để thực sự
        // gọi HTTP tới UserInfo endpoint của provider (Facebook/Twitter) và parse response.
        return super.loadUser(userRequest);
    }

    @Override
    @Transactional // Đảm bảo toàn bộ thao tác upsert User + SocialAccount là 1 transaction, rollback nếu lỗi
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // Lấy thông tin user thô từ provider thông qua hàm đã tách (dễ mock trong test)
        OAuth2User oAuth2User = fetchUserInfo(userRequest);

        // Lấy registrationId để biết đang xử lý provider nào (facebook/twitter)
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        // Chỉ lưu vào biến cục bộ — TUYỆT ĐỐI không log giá trị token (BR-002)
        String accessToken = userRequest.getAccessToken().getTokenValue();

        // Nếu là Twitter thì xử lý theo format response riêng (nested "data" object)
        if ("twitter".equals(registrationId)) {
            return handleTwitterUser(oAuth2User, accessToken);
        }
        // Mặc định còn lại xử lý như Facebook (attributes ở top-level)
        return handleFacebookUser(oAuth2User, accessToken);
    }

    @SuppressWarnings("unchecked") // Ép kiểu Object -> Map<String, Object> khi unwrap "data" của Twitter
    private OAuth2User handleTwitterUser(OAuth2User oAuth2User, String accessToken) {
        // DEC-001: Twitter/X trả về {"data": {"id":..., "name":..., "username":...}}
        // DefaultOAuth2UserService chỉ đọc top level; cần unwrap map data lồng bên trong.
        Map<String, Object> data;
        try {
            // Lấy giá trị attribute "data" (raw Object) rồi ép kiểu về Map
            data = (Map<String, Object>) oAuth2User.getAttributes().get("data");
        } catch (ClassCastException e) {
            // Nếu Twitter đổi format response và "data" không phải Map -> ném lỗi xác thực rõ ràng
            throw new OAuth2AuthenticationException("twitter_response_format_error");
        }
        if (data == null) {
            // Trường hợp response thiếu hẳn field "data" -> không thể tiếp tục xử lý
            throw new OAuth2AuthenticationException("twitter_missing_data");
        }

        // Lấy id định danh user từ phía Twitter (bắt buộc phải có)
        String providerUserId;
        try {
            providerUserId = (String) data.get("id");
        } catch (ClassCastException e) {
            throw new OAuth2AuthenticationException("twitter_invalid_user_id");
        }
        if (providerUserId == null) throw new OAuth2AuthenticationException("twitter_missing_user_id"); // Không có id thì không định danh được user
        // Lấy tên hiển thị (có thể null tùy cấu hình quyền của app Twitter)
        String name = (String) data.get("name");
        // Twitter không cung cấp email qua OAuth2; dùng placeholder ổn định để
        // thỏa mãn ràng buộc NOT NULL của User.email (BR-005/DISC-001).
        String placeholderEmail = "twitter:" + providerUserId + "@noemail.local";

        // Log debug nhưng KHÔNG log accessToken (tuân thủ BR-002)
        log.debug("Twitter login: registrationId=twitter, providerUserId={}", providerUserId);
        // Tạo mới hoặc cập nhật User + SocialAccount tương ứng trong DB
        upsertUser(SocialProvider.TWITTER, providerUserId, name, placeholderEmail, accessToken);

        // Trả về OAuth2User mới với "data" map làm attributes và "id" làm name-attribute-key,
        // vì object oAuth2User gốc không có key "id" ở top level (nó nằm trong "data")
        return new DefaultOAuth2User(oAuth2User.getAuthorities(), data, "id");
    }

    private OAuth2User handleFacebookUser(OAuth2User oAuth2User, String accessToken) {
        // Facebook trả attributes ở top-level nên lấy trực tiếp bằng getAttribute
        String providerUserId = oAuth2User.getAttribute("id");
        if (providerUserId == null) throw new OAuth2AuthenticationException("missing_provider_user_id"); // Không có id thì không định danh được user
        // Tên hiển thị của user trên Facebook (có thể null)
        String name = oAuth2User.getAttribute("name");
        // Email do Facebook trả về (có thể null nếu user không cấp quyền hoặc không có email xác thực)
        String email = oAuth2User.getAttribute("email");

        // Quyết định #3: chặn đăng nhập khi Facebook không cung cấp email
        if (email == null || email.isBlank()) {
            // Không có email thì không thể liên kết tài khoản theo Quyết định #4 -> từ chối đăng nhập
            throw new OAuth2AuthenticationException("email_required");
        }

        // Log debug nhưng KHÔNG log accessToken (tuân thủ BR-002)
        log.debug("Facebook login: registrationId=facebook, providerUserId={}", providerUserId);
        // Tạo mới hoặc cập nhật User + SocialAccount tương ứng trong DB
        upsertUser(SocialProvider.FACEBOOK, providerUserId, name, email, accessToken);

        // Facebook attributes đã đúng định dạng chuẩn nên trả thẳng oAuth2User gốc
        return oAuth2User;
    }

    private void upsertUser(SocialProvider provider, String providerUserId,
                            String name, String email, String accessToken) {
        // Quyết định #4: liên kết tài khoản theo email (email không null ở tất cả call site)
        // Nếu email đã tồn tại -> lấy User cũ; nếu chưa -> tạo User mới
        User user = userRepository.findByEmail(email).orElseGet(() -> createNewUser(name, email));

        // BR-003: upsert SocialAccount theo khóa (provider, providerAccountId)
        // Tìm bản ghi SocialAccount đã liên kết provider này với providerUserId này chưa,
        // nếu chưa có thì tạo instance SocialAccount mới (rỗng) để set dữ liệu bên dưới
        SocialAccount sa = socialAccountRepository
                .findByProviderAndProviderAccountId(provider, providerUserId)
                .orElseGet(SocialAccount::new);
        // Gắn SocialAccount này với User (mới hoặc đã tồn tại) ở trên
        sa.setUser(user);
        // Ghi nhận provider (FACEBOOK/TWITTER) cho bản ghi SocialAccount
        sa.setProvider(provider);
        // Lưu id định danh user phía provider để dùng làm khóa tra cứu lần sau
        sa.setProviderAccountId(providerUserId);
        // BR-005: lưu plaintext trong phạm vi D3 — mã hóa để sau
        // Lưu access token (hiện tại chưa mã hóa, sẽ cải tiến ở phase sau)
        sa.setAccessToken(accessToken);
        // Persist (insert hoặc update) bản ghi SocialAccount xuống DB
        socialAccountRepository.save(sa);
    }

    private User createNewUser(String name, String email) {
        // Khởi tạo entity User mới
        User user = new User();
        // Nếu provider không trả về tên thì dùng giá trị mặc định "Unknown"
        user.setName(name != null ? name : "Unknown");
        // Gán email (đã được đảm bảo not-null từ các call site)
        user.setEmail(email);
        // BR-001: luôn set USER tường minh — không phụ thuộc DB default ADMIN
        // Set role tường minh = USER để tránh rủi ro bảo mật nếu default DB thay đổi
        user.setRole(UserRole.USER);
        // Lưu User mới xuống DB và trả về entity đã có id
        return userRepository.save(user);
    }
}

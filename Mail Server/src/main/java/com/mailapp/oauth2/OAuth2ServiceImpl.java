package com.mailapp.oauth2;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mailapp.config.GoogleOAuthConfig;
import com.mailapp.dto.AuthorizeUrlResponse;
import com.mailapp.dto.GmailAccountResponse.LinkOutcome;
import com.mailapp.dto.LoginResponse;
import com.mailapp.dto.UserDto;
import com.mailapp.exception.AppException;
import com.mailapp.exception.GmailAccountChangedException;
import com.mailapp.exception.ReauthRequiredException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mailapp.mail.model.MailCredentials;
import com.mailapp.model.AccountStatus;
import com.mailapp.model.AuthType;
import com.mailapp.model.GmailAccount;
import com.mailapp.model.LinkIntent;
import com.mailapp.model.Role;
import com.mailapp.model.User;
import com.mailapp.oauth2.dto.GoogleTokenResponse;
import com.mailapp.oauth2.dto.GoogleGmailProfile;
import com.mailapp.oauth2.dto.GoogleUserInfo;
import com.mailapp.repository.GmailAccountRepository;
import com.mailapp.repository.GmailAccountSnapshot;
import com.mailapp.repository.UserRepository;
import com.mailapp.security.AesCipher;
import com.mailapp.security.JwtProvider;
import com.mailapp.service.AuditLogService;
import com.mailapp.service.GmailAccountLinkService;
import com.mailapp.service.SystemSettingService;
import com.mailapp.util.EmailNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Triển khai các nghiệp vụ liên quan đến Google OAuth2 và XOAUTH2.
 * Hỗ trợ PKCE (S256), State Store chống CSRF, cache access token trong bộ nhớ,
 * và xử lý làm mới/thu hồi token an toàn.
 *
 * @author Lê Thanh Cường (Module M06)
 */
@Service
public class OAuth2ServiceImpl implements OAuth2Service {

    private static final Logger log = LoggerFactory.getLogger(OAuth2ServiceImpl.class);

    private static final String GMAIL_SCOPES = "https://mail.google.com/ openid email profile";
    private static final int MAX_AVATAR_BYTES = 512 * 1024; // 512 KB

    // Cache entries belong to the exact linked grant, not just the row ID.
    // A null token is a rejected-grant marker for concurrent stale snapshots.
    private record CachedToken(String token, Instant expiresAt, String encryptedGrant, Long ownerId) {
        boolean matches(GmailAccount account) {
            return Objects.equals(encryptedGrant, account.getRefreshTokenEncrypted())
                    && Objects.equals(ownerId, account.getUser() == null ? null : account.getUser().getId());
        }
        @Override public String toString() { return "CachedToken[redacted]"; }
    }
    private static final ObjectMapper ERROR_JSON = new ObjectMapper();

    private final GoogleOAuthConfig oAuthConfig;
    private final OAuth2StateStore stateStore;
    private final GmailAccountRepository gmailAccountRepository;
    private final UserRepository userRepository;
    private final SystemSettingService systemSettingService;
    private final AuditLogService auditLogService;
    private final AesCipher aesCipher;
    private final RestClient restClient;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;
    private final GmailAccountLinkService linkService;

    // Bộ nhớ đệm Access Token trong RAM (hết hạn trước 60s, TUYỆT ĐỐI không lưu token vào DB)
    private final Cache<Long, CachedToken> tokenCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .build();

    // Bộ nhớ đệm ảnh đại diện Google trong RAM (TTL 1 giờ, tránh gọi Google lặp lại)
    private final Cache<Long, Optional<AvatarPayload>> avatarCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(java.time.Duration.ofHours(1))
            .build();

    // Khóa đồng bộ theo từng ID tài khoản Gmail để tránh 2 request cùng refresh một lúc
    private final Object[] accountLocks = new Object[256];
    {
        java.util.Arrays.setAll(accountLocks, ignored -> new Object());
    }

    public OAuth2ServiceImpl(GoogleOAuthConfig oAuthConfig,
                             OAuth2StateStore stateStore,
                             GmailAccountRepository gmailAccountRepository,
                             UserRepository userRepository,
                             SystemSettingService systemSettingService,
                             AuditLogService auditLogService,
                             AesCipher aesCipher,
                             RestClient googleRestClient) {
        this(oAuthConfig, stateStore, gmailAccountRepository, userRepository,
                systemSettingService, auditLogService, aesCipher, googleRestClient, null, null, null);
    }

    public OAuth2ServiceImpl(GoogleOAuthConfig oAuthConfig,
                             OAuth2StateStore stateStore,
                             GmailAccountRepository gmailAccountRepository,
                             UserRepository userRepository,
                             SystemSettingService systemSettingService,
                             AuditLogService auditLogService,
                             AesCipher aesCipher,
                             RestClient googleRestClient,
                             JwtProvider jwtProvider,
                             PasswordEncoder passwordEncoder) {
        this(oAuthConfig, stateStore, gmailAccountRepository, userRepository,
                systemSettingService, auditLogService, aesCipher, googleRestClient, jwtProvider, passwordEncoder, null);
    }

    @Autowired
    public OAuth2ServiceImpl(GoogleOAuthConfig oAuthConfig,
                             OAuth2StateStore stateStore,
                             GmailAccountRepository gmailAccountRepository,
                             UserRepository userRepository,
                             SystemSettingService systemSettingService,
                             AuditLogService auditLogService,
                             AesCipher aesCipher,
                             RestClient googleRestClient,
                             @Autowired(required = false) JwtProvider jwtProvider,
                             @Autowired(required = false) PasswordEncoder passwordEncoder,
                             @Autowired(required = false) GmailAccountLinkService linkService) {
        this.oAuthConfig = oAuthConfig;
        this.stateStore = stateStore;
        this.gmailAccountRepository = gmailAccountRepository;
        this.userRepository = userRepository;
        this.systemSettingService = systemSettingService;
        this.auditLogService = auditLogService;
        this.aesCipher = aesCipher;
        this.restClient = googleRestClient;
        this.jwtProvider = jwtProvider;
        this.passwordEncoder = passwordEncoder;
        this.linkService = linkService != null
                ? linkService
                : new GmailAccountLinkService(gmailAccountRepository, systemSettingService, auditLogService, null);
    }

    @Override
    public AuthorizeUrlResponse getAuthorizeUrl(Long userId) {
        return getAuthorizeUrl(userId, null);
    }

    @Override
    public AuthorizeUrlResponse getAuthorizeUrl(Long userId, Long existingAccountId) {
        return getAuthorizeUrl(userId, existingAccountId,
                existingAccountId == null ? LinkIntent.CREATE : LinkIntent.UPSERT);
    }

    @Override
    public AuthorizeUrlResponse getAuthorizeUrl(Long userId, Long existingAccountId, LinkIntent intent) {
        if (existingAccountId != null && intent == LinkIntent.CREATE) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "CREATE không nhận accountId.");
        }
        if (!systemSettingService.isOAuth2LinkEnabled()) {
            throw new AppException(HttpStatus.FORBIDDEN, "FEATURE_DISABLED",
                    "Tính năng liên kết Gmail bằng OAuth2 hiện đang bị tạm tắt bởi quản trị viên.");
        }
        oAuthConfig.validateConfig();

        // Chỉ kiểm tra giới hạn MAX_GMAIL_PER_USER nếu không phải đang thêm/cấp lại quyền cho hộp thư đã tồn tại của user
        boolean forExisting = existingAccountId != null
                && gmailAccountRepository.findByIdAndUserId(existingAccountId, userId).isPresent();
        if (existingAccountId != null && !forExisting) {
            throw new AppException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Không tìm thấy tài khoản Gmail.");
        }
        if (!forExisting && intent != LinkIntent.UPSERT) {
            int maxGmail = systemSettingService.getMaxGmailPerUser();
            if (maxGmail > 0 && gmailAccountRepository.countByUserId(userId) >= maxGmail) {
                throw new AppException(HttpStatus.CONFLICT, "GMAIL_LIMIT_REACHED",
                        "Bạn đã đạt giới hạn tối đa số lượng tài khoản Gmail được liên kết (" + maxGmail + ").");
            }
        }

        // Sinh PKCE code_verifier và code_challenge (S256)
        String codeVerifier = PkceUtil.generateCodeVerifier();
        String codeChallenge = PkceUtil.generateCodeChallenge(codeVerifier);

        // Sinh state ngẫu nhiên và lưu ánh xạ tới userId và codeVerifier trong Caffeine (TTL 5 phút)
        OAuth2StateStore.LinkStatePair pair = stateStore.generateLinkState(userId, codeVerifier, existingAccountId, intent);

        String encodedRedirectUri = URLEncoder.encode(oAuthConfig.getRedirectUri(), StandardCharsets.UTF_8);
        String encodedScope = URLEncoder.encode(GMAIL_SCOPES, StandardCharsets.UTF_8);

        String authorizeUrl = String.format(
                "%s?client_id=%s&redirect_uri=%s&response_type=code&scope=%s&access_type=offline&prompt=consent&include_granted_scopes=true&state=%s&code_challenge=%s&code_challenge_method=S256",
                oAuthConfig.getAuthUrl(),
                oAuthConfig.getClientId(),
                encodedRedirectUri,
                encodedScope,
                pair.state(),
                codeChallenge
        );

        log.info("Đã tạo Google OAuth2 authorize URL cho userId={} (PKCE S256)", userId);
        return new AuthorizeUrlResponse(authorizeUrl, pair.pollToken());
    }

    @Override
    public LinkPollResponse pollLink(String pollToken, Long userId) {
        OAuth2StateStore.LinkPollEntry entry = stateStore.pollLinkSession(pollToken, userId)
                .orElseThrow(() -> new AppException(HttpStatus.GONE, "LINK_SESSION_EXPIRED",
                        "Phiên liên kết Gmail đã hết hạn hoặc đã được sử dụng."));
        if (!java.util.Objects.equals(entry.userId(), userId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "LINK_SESSION_NOT_FOUND", "Không tìm thấy phiên liên kết.");
        }
        return new LinkPollResponse(entry.status().name(), entry.accountId(), entry.outcome(), entry.code(), entry.message());
    }

    @Override
    public GoogleLoginStartResponse startGoogleLogin() {
        if (!systemSettingService.isOAuth2LinkEnabled()) {
            throw new AppException(HttpStatus.FORBIDDEN, "FEATURE_DISABLED",
                    "Tính năng đăng nhập bằng Google OAuth2 hiện đang bị tạm tắt bởi quản trị viên.");
        }
        oAuthConfig.validateConfig();

        String codeVerifier = PkceUtil.generateCodeVerifier();
        String codeChallenge = PkceUtil.generateCodeChallenge(codeVerifier);
        OAuth2StateStore.LoginStatePair pair = stateStore.generateGoogleLoginState(codeVerifier);

        String encodedRedirectUri = URLEncoder.encode(oAuthConfig.getRedirectUri(), StandardCharsets.UTF_8);
        String encodedScope = URLEncoder.encode(GMAIL_SCOPES, StandardCharsets.UTF_8);

        String authorizeUrl = String.format(
                "%s?client_id=%s&redirect_uri=%s&response_type=code&scope=%s&access_type=offline&prompt=consent&include_granted_scopes=true&state=%s&code_challenge=%s&code_challenge_method=S256",
                oAuthConfig.getAuthUrl(),
                oAuthConfig.getClientId(),
                encodedRedirectUri,
                encodedScope,
                pair.state(),
                codeChallenge
        );

        log.info("Đã khởi tạo phiên Đăng nhập Google OAuth2 (PKCE S256)");
        return new GoogleLoginStartResponse(authorizeUrl, pair.pollToken());
    }

    @Override
    public GoogleLoginPollResponse pollGoogleLogin(String pollToken) {
        if (pollToken == null || pollToken.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Thiếu mã pollToken.");
        }
        Optional<OAuth2StateStore.LoginPollEntry> entryOpt = stateStore.pollLoginSession(pollToken);
        if (entryOpt.isEmpty()) {
            throw new AppException(HttpStatus.GONE, "LOGIN_SESSION_EXPIRED",
                    "Phiên đăng nhập Google đã hết hạn hoặc đã được sử dụng. Vui lòng thử lại.");
        }
        OAuth2StateStore.LoginPollEntry entry = entryOpt.get();
        return new GoogleLoginPollResponse(
                entry.status().name(),
                entry.loginResponse(),
                entry.errorCode(),
                entry.errorMessage()
        );
    }

    @Override
    public String handleCallback(String code, String state, String error, String errorDescription) {
        if (error != null && !error.isBlank()) {
            log.warn("Google OAuth2 callback bị từ chối hoặc trả lỗi.");
            String message = "Bạn đã hủy hoặc từ chối cấp quyền truy cập tài khoản Google.";
            if (state != null && !state.isBlank()) {
                stateStore.validateAndConsumeState(state).ifPresent(se -> completeCallbackFailure(se, "OAUTH2_DENIED", message));
            }
            return renderHtmlResult(false, null, message);
        }

        if (state == null || state.isBlank()) {
            return renderHtmlResult(false, null, "Mã xác thực trạng thái (state) bị thiếu từ phản hồi Google.");
        }

        Optional<OAuth2StateStore.StateEntry> stateOpt = stateStore.validateAndConsumeState(state);
        if (stateOpt.isEmpty()) {
            log.warn("OAuth2 callback với state không hợp lệ, đã hết hạn hoặc đã được sử dụng.");
            return renderHtmlResult(false, null, "Mã xác thực trạng thái (state) không hợp lệ, đã hết hạn hoặc đã được sử dụng. Vui lòng mở ứng dụng và thử lại.");
        }

        OAuth2StateStore.StateEntry entry = stateOpt.get();
        boolean isGoogleLogin = entry.flowType() == OAuth2StateStore.FlowType.GOOGLE_LOGIN;
        String pollToken = entry.pollToken();
        Long userId = entry.userId();
        String codeVerifier = entry.codeVerifier();

        if (!systemSettingService.isOAuth2LinkEnabled()) {
            String message = "Tính năng Google OAuth2 hiện đang bị tắt.";
            return callbackFailure(entry, "FEATURE_DISABLED", null, message);
        }

        if (code == null || code.isBlank()) {
            return callbackFailure(entry, "MISSING_CODE", null, "Không nhận được mã ủy quyền (code) từ Google.");
        }

        GoogleTokenResponse tokenResponse;
        try {
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("code", code);
            body.add("client_id", oAuthConfig.getClientId());
            body.add("client_secret", oAuthConfig.getClientSecret());
            body.add("redirect_uri", oAuthConfig.getRedirectUri());
            body.add("grant_type", "authorization_code");
            body.add("code_verifier", codeVerifier);

            tokenResponse = restClient.post()
                    .uri(oAuthConfig.getTokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(GoogleTokenResponse.class);
        } catch (Exception e) {
            log.error("Lỗi khi đổi code lấy token từ Google OAuth2: {}", e.getClass().getSimpleName());
            return callbackFailure(entry, "GOOGLE_TOKEN_ERROR", null, "Không thể kết nối đến máy chủ Google để đổi mã truy cập. Vui lòng thử lại.");
        }

        if (tokenResponse == null || tokenResponse.accessToken() == null || tokenResponse.accessToken().isBlank()
                || (tokenResponse.expiresIn() != null && tokenResponse.expiresIn() <= 0)
                || (tokenResponse.tokenType() != null && !"Bearer".equalsIgnoreCase(tokenResponse.tokenType()))) {
            return callbackFailure(entry, "GOOGLE_TOKEN_ERROR", null, "Không nhận được access token từ máy chủ Google.");
        }

        // Kiểm tra granular consent: người dùng có thể bỏ tick quyền truy cập Gmail trên màn hình Google
        if (!hasRequiredGmailScope(tokenResponse.scope())) {
            log.warn("OAuth2 callback thiếu quyền Gmail (https://mail.google.com/) cho userId={}", userId);
            auditLogService.log(userId, "LINK_GMAIL_DENIED_SCOPE",
                    "Người dùng chưa cấp quyền truy cập Gmail (thiếu scope https://mail.google.com/)", (String) null);
            String msg = "Bạn chưa cấp quyền truy cập Gmail, hãy bấm Liên kết lại và giữ nguyên các ô quyền.";
            return callbackFailure(entry, "MISSING_GMAIL_SCOPE", null, msg);
        }

        GoogleUserInfo userInfo;
        try {
            userInfo = restClient.get()
                    .uri(oAuthConfig.getUserinfoUrl())
                    .header("Authorization", "Bearer " + tokenResponse.accessToken())
                    .retrieve()
                    .body(GoogleUserInfo.class);
        } catch (Exception e) {
            log.error("Lỗi khi lấy thông tin người dùng từ Google UserInfo: {}", e.getClass().getSimpleName());
            return callbackFailure(entry, "GOOGLE_USERINFO_ERROR", null, "Không thể xác định địa chỉ email của tài khoản Google vừa ủy quyền.");
        }

        if (userInfo == null || userInfo.email() == null || userInfo.email().isBlank()) {
            return callbackFailure(entry, "GOOGLE_USERINFO_ERROR", null, "Không thể đọc thông tin email từ Google.");
        }

        if (!userInfo.isEmailVerified()) {
            String msg = "Địa chỉ email Google chưa được xác thực (email_verified=false).";
            return callbackFailure(entry, "EMAIL_NOT_VERIFIED", userInfo.email(), msg);
        }

        String email = userInfo.email().toLowerCase(java.util.Locale.ROOT).trim();
        // The OIDC identity and the Gmail mailbox must both belong to this same
        // access token. Do not infer mailbox identity from a display address.
        GoogleGmailProfile profile;
        try {
            profile = restClient.get()
                    .uri(oAuthConfig.getProfileUrl())
                    .header("Authorization", "Bearer " + tokenResponse.accessToken())
                    .retrieve()
                    .body(GoogleGmailProfile.class);
        } catch (Exception e) {
            log.warn("Không thể đọc Gmail profile: {}", e.getClass().getSimpleName());
            return callbackFailure(entry, "GOOGLE_PROFILE_ERROR", null,
                    "Không thể xác định hộp thư Gmail bằng quyền vừa được cấp. Vui lòng thử lại.");
        }
        if (profile == null || profile.emailAddress() == null || profile.emailAddress().isBlank()) {
            return callbackFailure(entry, "GOOGLE_PROFILE_ERROR", null, "Gmail không trả về địa chỉ hộp thư.");
        }
        if (!email.equalsIgnoreCase(profile.emailAddress().trim())) {
            return callbackFailure(entry, "ACCOUNT_MISMATCH", null,
                    "Địa chỉ hộp thư Gmail không khớp danh tính Google đã xác thực.");
        }
        String maskedEmail = MailCredentials.maskEmail(email);
        long expiresIn = tokenResponse.expiresIn() != null ? tokenResponse.expiresIn() : 3600L;

        // ==================== LUỒNG 1: ĐĂNG NHẬP BẰNG GOOGLE OAUTH2 (HỢP NHẤT KHI TRÙNG EMAIL) ====================
        if (isGoogleLogin) {
            try {
                return completeGoogleLoginCallback(pollToken, email, maskedEmail, userInfo, tokenResponse, expiresIn);
            } catch (RuntimeException ex) {
                log.warn("Không thể hoàn tất đăng nhập Google: {}", ex.getClass().getSimpleName());
                return callbackFailure(entry, "INTERNAL_SERVER_ERROR", email, "Không thể hoàn tất đăng nhập Google. Vui lòng thử lại.");
            }
        }

        // ==================== LUỒNG 2: LIÊN KẾT HOẶC THÊM PHƯƠNG THỨC GOOGLE OAUTH2 CHO USER ĐÃ ĐĂNG NHẬP ====================
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return callbackFailure(entry, "USER_NOT_FOUND", email, "Không tìm thấy thông tin tài khoản người dùng tương ứng trên hệ thống.");
        }
        // State proves who started the flow, not that permission is still current.
        if (!user.isEnabled()) {
            return callbackFailure(entry, "ACCOUNT_LOCKED", email, "Tài khoản của bạn đã bị khóa.");
        }
        if (user.isMustChangePassword()) {
            return callbackFailure(entry, "PASSWORD_CHANGE_REQUIRED", email,
                    "Tài khoản yêu cầu bắt buộc phải đổi mật khẩu trước khi liên kết Gmail.");
        }
        if (systemSettingService.isMaintenanceMode() && user.getRole() != Role.ADMIN) {
            return callbackFailure(entry, "MAINTENANCE_MODE", email, "Hệ thống đang bảo trì.");
        }
        if (!systemSettingService.isOAuth2LinkEnabled()) {
            return callbackFailure(entry, "FEATURE_DISABLED", email, "Tính năng liên kết Gmail bằng OAuth2 hiện đang bị tắt.");
        }

        try {
            String encryptedRefresh = (tokenResponse.refreshToken() != null && !tokenResponse.refreshToken().isBlank())
                    ? aesCipher.encrypt(tokenResponse.refreshToken()) : null;
            String trustedAvatar = isTrustedGoogleAvatarUrl(userInfo.picture()) ? userInfo.picture().trim() : null;
            GmailAccountLinkService.UpsertResult upsert = linkService.upsertOAuth2(
                    user,
                    email,
                    encryptedRefresh,
                    userInfo.name(),
                    trustedAvatar,
                    LocalDateTime.now().plusSeconds(expiresIn),
                    entry.accountId(), entry.intent()
            );
            GmailAccount saved = upsert.account();
            if (saved != null && saved.getId() != null) {
                rememberToken(saved, tokenResponse.accessToken(), Instant.now().plusSeconds(expiresIn));
                avatarCache.invalidate(saved.getId());
                stateStore.completeLinkSuccess(pollToken, saved.getId(), upsert.outcome(), upsert.message());
            }

            String heading = switch (upsert.outcome()) {
                case CREATED -> "Liên kết thành công";
                case METHOD_ADDED -> "Đã thêm phương thức đăng nhập";
                case METHOD_UPDATED -> "Cập nhật ủy quyền thành công";
            };
            String htmlMessage = switch (upsert.outcome()) {
                case CREATED -> "Tài khoản <strong>" + maskedEmail + "</strong> đã được liên kết thành công vào <strong>Veyra Mail</strong>!";
                case METHOD_ADDED -> "Đã thêm phương thức <strong>Google OAuth2</strong> cho hộp thư <strong>" + maskedEmail
                        + "</strong> (đặt làm phương thức chính, vẫn giữ App Password dự phòng).";
                case METHOD_UPDATED -> "Cập nhật ủy quyền thành công cho hộp thư <strong>" + maskedEmail + "</strong>.";
            };
            return renderHtmlResult(true, heading, email, htmlMessage);
        } catch (AppException ex) {
            return callbackFailure(entry, ex.getCode(), email, ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("Không thể lưu liên kết OAuth2: {}", ex.getClass().getSimpleName());
            return callbackFailure(entry, "INTERNAL_SERVER_ERROR", email, "Không thể lưu liên kết Gmail. Vui lòng thử lại.");
        }
    }

    private String callbackFailure(OAuth2StateStore.StateEntry entry, String code, String email, String message) {
        completeCallbackFailure(entry, code, message);
        return renderHtmlResult(false, email, message);
    }

    private void completeCallbackFailure(OAuth2StateStore.StateEntry entry, String code, String message) {
        if (entry.flowType() == OAuth2StateStore.FlowType.GOOGLE_LOGIN) {
            stateStore.completeGoogleLoginFailure(entry.pollToken(), code, message);
        } else {
            stateStore.completeLinkFailure(entry.pollToken(), code, message);
        }
    }

    @Override
    public String getFreshAccessToken(GmailAccount account) {
        if (!account.hasOAuth() && account.getAuthType() != AuthType.OAUTH2) {
            throw new IllegalArgumentException("Tài khoản chưa có phương thức xác thực OAuth2.");
        }

        Long accountId = account.getId();
        String maskedEmail = MailCredentials.maskEmail(account.getEmail());

        // Bounded striped locks avoid retaining an object for every deleted
        // mailbox. Transient accounts never share the cache or a grant entry.
        synchronized (accountLock(accountId)) {
            // A competing relink/removal/rotation may have committed while
            // this caller waited for the stripe. Do not reuse a cache entry or
            // send an obsolete refresh grant to the provider in that case.
            GmailAccountSnapshot.requireCurrent(gmailAccountRepository, account);
            if (account.getStatus() == AccountStatus.NEEDS_REAUTH) {
                throw new ReauthRequiredException();
            }
            CachedToken cached = accountId == null ? null : tokenCache.getIfPresent(accountId);
            if (cached != null && cached.matches(account)) {
                if (cached.token() == null) throw new ReauthRequiredException();
                if (Instant.now().plusSeconds(60).isBefore(cached.expiresAt())) return cached.token();
            }

            oAuthConfig.validateConfig();

            String decryptedRefreshToken = account.hasOAuth() ? aesCipher.decrypt(account.getRefreshTokenEncrypted()) : null;
            if (decryptedRefreshToken == null || decryptedRefreshToken.isBlank()) {
                rejectGrant(account, maskedEmail);
                throw new ReauthRequiredException("Không tìm thấy Refresh Token hợp lệ cho Gmail " + maskedEmail + ". Vui lòng liên kết lại.");
            }

            try {
                MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
                body.add("client_id", oAuthConfig.getClientId());
                body.add("client_secret", oAuthConfig.getClientSecret());
                body.add("refresh_token", decryptedRefreshToken);
                body.add("grant_type", "refresh_token");

                GoogleTokenResponse response = restClient.post()
                        .uri(oAuthConfig.getTokenUrl())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(body)
                        .retrieve()
                        .body(GoogleTokenResponse.class);

                if (response == null || response.accessToken() == null || response.accessToken().isBlank()
                        || (response.expiresIn() != null && response.expiresIn() <= 0)
                        || (response.tokenType() != null && !"Bearer".equalsIgnoreCase(response.tokenType()))) {
                    throw new AppException(HttpStatus.BAD_GATEWAY, "GMAIL_CONNECTION_ERROR",
                            "Phản hồi làm mới token từ Google không hợp lệ.");
                }
                // RFC 6749 permits an omitted scope when unchanged. An
                // explicitly reduced scope must never be cached or used.
                if (response.scope() != null && !hasRequiredGmailScope(response.scope())) {
                    rejectGrant(account, maskedEmail);
                    throw new ReauthRequiredException("Quyền truy cập Gmail không còn đầy đủ. Vui lòng cấp lại quyền.");
                }

                long expiresIn = response.expiresIn() != null ? response.expiresIn() : 3600L;
                Instant expiry = Instant.now().plusSeconds(expiresIn);
                account.setTokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
                // A rotated refresh token is part of the new grant; never
                // publish an access token before the corresponding row commits.
                if (response.refreshToken() != null && !response.refreshToken().isBlank()) {
                    account.setRefreshTokenEncrypted(aesCipher.encrypt(response.refreshToken()));
                }
                saveRefreshState(account);
                rememberToken(account, response.accessToken(), expiry);

                log.info("Làm mới Access Token thành công cho: {}", maskedEmail);
                return response.accessToken();

            } catch (RestClientResponseException e) {
                // Khi Google trả về 400 Bad Request kèm invalid_grant (người dùng thu hồi quyền trên Google Account)
                log.warn("Lỗi làm mới token cho email {}: status={}", maskedEmail, e.getStatusCode());
                if (e.getStatusCode().value() != 400 || !isInvalidGrant(e)) {
                    throw new AppException(HttpStatus.BAD_GATEWAY, "GMAIL_CONNECTION_ERROR",
                            "Không thể làm mới phiên Google. Vui lòng thử lại sau.");
                }

                rejectGrant(account, maskedEmail);

                throw new ReauthRequiredException(
                        "Quyền truy cập Gmail (" + maskedEmail + ") đã bị thu hồi hoặc hết hạn. Vui lòng liên kết lại tài khoản.");
            } catch (AppException | ReauthRequiredException e) {
                throw e;
            } catch (Exception e) {
                log.error("Lỗi kết nối khi làm mới token Google cho {}: {}", maskedEmail, e.getClass().getSimpleName());
                throw new AppException(HttpStatus.BAD_GATEWAY, "GMAIL_CONNECTION_ERROR",
                        "Không thể kết nối đến máy chủ Google để làm mới phiên làm việc.", e);
            }
        }
    }

    private Object accountLock(Long accountId) {
        return accountId == null ? new Object() : accountLocks[Math.floorMod(accountId.hashCode(), accountLocks.length)];
    }

    private void rememberToken(GmailAccount account, String token, Instant expiry) {
        if (account.getId() == null || !account.hasOAuth()) return;
        synchronized (accountLock(account.getId())) {
            tokenCache.put(account.getId(), new CachedToken(token, expiry,
                    account.getRefreshTokenEncrypted(), account.getUser() == null ? null : account.getUser().getId()));
        }
    }

    private void saveRefreshState(GmailAccount account) {
        GmailAccountSnapshot.saveState(gmailAccountRepository, account);
    }

    private void rejectGrant(GmailAccount account, String maskedEmail) {
        // Keep a newer, independently linked grant untouched.
        if (account.getId() != null) {
            tokenCache.asMap().computeIfPresent(account.getId(), (id, entry) -> entry.matches(account) ? null : entry);
        }
        account.setStatus(AccountStatus.NEEDS_REAUTH);
        account.setFallbackActive(false);
        saveRefreshState(account);
        rememberToken(account, null, Instant.MAX);
        auditLogService.log(account.getUser() != null ? account.getUser().getId() : null,
                "GMAIL_NEEDS_REAUTH", "Quyền truy cập cần cấp lại: " + maskedEmail, (String) null);
    }

    private static boolean isInvalidGrant(RestClientResponseException ex) {
        try {
            return "invalid_grant".equals(ERROR_JSON.readTree(ex.getResponseBodyAsString()).path("error").asText());
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public void revokeToken(GmailAccount account) {
        if (!account.hasOAuth() && account.getAuthType() != AuthType.OAUTH2) {
            return;
        }
        // Clear local caches even when a stored credential cannot be decrypted.
        if (account.getId() != null) {
            tokenCache.asMap().computeIfPresent(account.getId(), (id, entry) -> entry.matches(account) ? null : entry);
            avatarCache.invalidate(account.getId());
        }
        String refreshToken = aesCipher.decrypt(account.getRefreshTokenEncrypted());
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        try {
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("token", refreshToken);

            restClient.post()
                    .uri(oAuthConfig.getRevokeUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Đã gửi yêu cầu thu hồi token Google thành công cho: {}", MailCredentials.maskEmail(account.getEmail()));
        } catch (Exception e) {
            // The unlink/delete caller records the failure and continues local deletion.
            throw new IllegalStateException("Không thể thu hồi token trên Google.");
        }
    }

    /**
     * Kiểm tra chuỗi scope do Google trả về có chứa quyền truy cập Gmail bắt buộc (https://mail.google.com/) hay không.
     */
    private boolean hasRequiredGmailScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return false;
        }
        for (String token : scope.trim().split("\\s+")) {
            if ("https://mail.google.com/".equals(token) || "https://mail.google.com".equals(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Render trang HTML tiếng Việt phản hồi trực quan trên trình duyệt người dùng.
     * Hỗ trợ Dark Mode, bảo mật không lộ token/secret, và hiển thị email đã che.
     */
    /**
     * Xử lý hoàn tất luồng Đăng nhập trực tiếp bằng Google OAuth2:
     * - Nếu email đã tồn tại trong bảng users (đăng ký bằng email/username + mật khẩu trước đó):
     *   hợp nhất an toàn vào tài khoản hiện có (vì Google đã xác thực email_verified=true), giữ nguyên vai trò & mật khẩu,
     *   đồng thời tự động liên kết/nâng cấp GmailAccount sang OAuth2 + ảnh đại diện.
     * - Nếu là email mới hoàn toàn: tự động tạo tài khoản USER + liên kết sẵn GmailAccount OAuth2.
     */
    private String completeGoogleLoginCallback(String pollToken,
                                               String email,
                                               String maskedEmail,
                                               GoogleUserInfo userInfo,
                                               GoogleTokenResponse tokenResponse,
                                               long expiresIn) {
        if (jwtProvider == null || passwordEncoder == null) {
            stateStore.completeGoogleLoginFailure(pollToken, "INTERNAL_ERROR", "Dịch vụ phát hành JWT chưa sẵn sàng.");
            return renderHtmlResult(false, email, "Dịch vụ đăng nhập Google chưa được cấu hình đầy đủ trên máy chủ.");
        }

        Optional<User> existingUserOpt = userRepository.findByEmailIgnoreCase(email);
        User user;
        boolean mergedExisting;

        if (existingUserOpt.isPresent()) {
            user = existingUserOpt.get();
            mergedExisting = true;

            if (!user.isEnabled()) {
                auditLogService.log(user.getId(), "GOOGLE_LOGIN_FAILED", "ACCOUNT_LOCKED", (String) null);
                String msg = "Tài khoản của bạn đã bị khóa. Vui lòng liên hệ Quản trị viên.";
                stateStore.completeGoogleLoginFailure(pollToken, "ACCOUNT_LOCKED", msg);
                return renderHtmlResult(false, email, msg);
            }
            if (systemSettingService.isMaintenanceMode() && user.getRole() != Role.ADMIN) {
                auditLogService.log(user.getId(), "GOOGLE_LOGIN_FAILED", "MAINTENANCE_MODE", (String) null);
                String msg = "Hệ thống đang bảo trì. Chỉ Quản trị viên (ADMIN) mới có thể đăng nhập.";
                stateStore.completeGoogleLoginFailure(pollToken, "MAINTENANCE_MODE", msg);
                return renderHtmlResult(false, email, msg);
            }
            if ((user.getFullName() == null || user.getFullName().isBlank()) && userInfo.name() != null && !userInfo.name().isBlank()) {
                String name = userInfo.name().trim();
                // Avoid merging a stale user snapshot over concurrent security updates.
                userRepository.fillMissingFullName(user.getId(), name.substring(0, Math.min(name.length(), 100)), LocalDateTime.now());
                user = userRepository.findById(user.getId())
                        .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy người dùng."));
            }
        } else {
            mergedExisting = false;
            if (systemSettingService.isMaintenanceMode()) {
                String msg = "Hệ thống đang bảo trì, tạm thời không nhận đăng ký mới qua Google.";
                stateStore.completeGoogleLoginFailure(pollToken, "MAINTENANCE_MODE", msg);
                return renderHtmlResult(false, email, msg);
            }
            if (!systemSettingService.isRegistrationEnabled()) {
                String msg = "Tính năng tạo tài khoản mới hiện đang bị tắt bởi Quản trị viên.";
                stateStore.completeGoogleLoginFailure(pollToken, "REGISTRATION_DISABLED", msg);
                return renderHtmlResult(false, email, msg);
            }

            user = new User();
            user.setUsername(generateUniqueUsernameFromEmail(email));
            // Sinh mật khẩu ngẫu nhiên 256-bit rồi băm BCrypt (chống đăng nhập mật khẩu đoán mò cho user chỉ dùng Google)
            byte[] rnd = new byte[32];
            new java.security.SecureRandom().nextBytes(rnd);
            user.setPasswordHash(passwordEncoder.encode(java.util.Base64.getEncoder().encodeToString(rnd)));
            user.setEmail(email);
            user.setFullName(userInfo.name() != null && !userInfo.name().isBlank() ? userInfo.name().trim() : email);
            user.setRole(Role.USER);
            user.setEnabled(true);
            user.setMustChangePassword(false);
            user = userRepository.save(user);
        }

        // Tự động liên kết hoặc thêm OAuth2 vào GmailAccount tương ứng (GIỮ NGUYÊN App Password nếu đã có!)
        String encryptedRefresh = tokenResponse.refreshToken() != null && !tokenResponse.refreshToken().isBlank()
                ? aesCipher.encrypt(tokenResponse.refreshToken()) : null;
        GmailAccount gmailAccount = null;
        String linkWarning = null;
        try {
            gmailAccount = linkService.upsertOAuth2(user, email, encryptedRefresh, userInfo.name(),
                    isTrustedGoogleAvatarUrl(userInfo.picture()) ? userInfo.picture().trim() : null,
                    LocalDateTime.now().plusSeconds(expiresIn)).account();
        } catch (AppException ex) {
            // Authentication and mailbox linking are separate: a user can log in to
            // change their password, but cannot auto-link a mailbox before doing so.
            if ("GMAIL_LIMIT_REACHED".equals(ex.getCode()) || "MISSING_REFRESH_TOKEN".equals(ex.getCode())
                    || "PASSWORD_CHANGE_REQUIRED".equals(ex.getCode())) {
                linkWarning = ex.getMessage();
            } else {
                stateStore.completeGoogleLoginFailure(pollToken, ex.getCode(), ex.getMessage());
                return renderHtmlResult(false, email, ex.getMessage());
            }
        }

        if (gmailAccount != null && gmailAccount.getId() != null) {
            rememberToken(gmailAccount, tokenResponse.accessToken(), Instant.now().plusSeconds(expiresIn));
            avatarCache.invalidate(gmailAccount.getId());
        }

        String accessToken = jwtProvider.generateToken(user);
        LoginResponse loginResponse = LoginResponse.of(accessToken, jwtProvider.getExpirationSeconds(), UserDto.from(user));
        stateStore.completeGoogleLoginSuccess(pollToken, loginResponse);

        auditLogService.log(user.getId(), mergedExisting ? "GOOGLE_LOGIN_MERGED" : "GOOGLE_LOGIN_NEW",
                "Đăng nhập Google OAuth2: " + maskedEmail, (String) null);
        log.info("Đăng nhập Google OAuth2 thành công cho userId={} (mergedExisting={}, email={})",
                user.getId(), mergedExisting, maskedEmail);

        String htmlMsg = mergedExisting
                ? "Đăng nhập thành công! Tài khoản cùng email <strong>" + maskedEmail + "</strong> đã được hợp nhất tự động với phiên <strong>Google OAuth2</strong>."
                : "Chào mừng! Tài khoản <strong>" + maskedEmail + "</strong> đã được tạo và liên kết sẵn vào <strong>Veyra Mail</strong>.";
        if (linkWarning != null) {
            htmlMsg = "Đăng nhập thành công. Chưa liên kết được hộp thư Gmail: " + linkWarning;
        }
        return renderHtmlResult(true, email, htmlMsg);
    }

    private String generateUniqueUsernameFromEmail(String email) {
        String local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        String base = local.replaceAll("[^a-zA-Z0-9_]", "_");
        if (base.length() < 3) base = base + "_usr";
        if (base.length() > 40) base = base.substring(0, 40);
        if (!userRepository.existsByUsername(base)) {
            return base;
        }
        for (int i = 2; i < 1000; i++) {
            String candidate = base + "_" + i;
            if (!userRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }
        return base + "_" + (System.currentTimeMillis() % 100000);
    }

    /**
     * Kiểm tra chặt URL ảnh đại diện thuộc host ảnh chính thức của Google (chống SSRF).
     */
    static boolean isTrustedGoogleAvatarUrl(String url) {
        if (url == null || url.isBlank() || url.length() > 512) return false;
        try {
            java.net.URI uri = java.net.URI.create(url.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
            String host = uri.getHost();
            if (host == null) return false;
            String lower = host.toLowerCase(java.util.Locale.ROOT);
            return lower.matches("^lh[0-9]+\\.googleusercontent\\.com$")
                    || lower.equals("www.googleusercontent.com");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Optional<AvatarPayload> fetchAccountAvatar(GmailAccount account) {
        if (account == null || account.getId() == null) {
            return Optional.empty();
        }
        Optional<AvatarPayload> cached = avatarCache.getIfPresent(account.getId());
        if (cached != null) {
            return cached;
        }

        String avatarUrl = account.getAvatarUrl();
        // Tự động bổ sung avatarUrl + displayName cho tài khoản OAuth2 đã liên kết trước đây nếu còn trống
        if ((avatarUrl == null || avatarUrl.isBlank()) && account.getAuthType() == AuthType.OAUTH2) {
            try {
                String accessToken = getFreshAccessToken(account);
                GoogleUserInfo info = restClient.get()
                        .uri(oAuthConfig.getUserinfoUrl())
                        .header("Authorization", "Bearer " + accessToken)
                        .retrieve()
                        .body(GoogleUserInfo.class);
                if (info != null) {
                    boolean changed = false;
                    if (isTrustedGoogleAvatarUrl(info.picture())) {
                        avatarUrl = info.picture().trim();
                        account.setAvatarUrl(avatarUrl);
                        changed = true;
                    }
                    if (info.name() != null && !info.name().isBlank()
                            && (account.getDisplayName() == null || account.getDisplayName().equalsIgnoreCase(account.getEmail()))) {
                        account.setDisplayName(info.name().trim());
                        changed = true;
                    }
                    if (changed) {
                        gmailAccountRepository.save(account);
                    }
                }
            } catch (Exception e) {
                log.debug("Không thể tự bổ sung Google UserInfo cho accountId={}: {}", account.getId(), e.getClass().getSimpleName());
            }
        }

        if (!isTrustedGoogleAvatarUrl(avatarUrl)) {
            avatarCache.put(account.getId(), Optional.empty());
            return Optional.empty();
        }

        try {
            org.springframework.http.ResponseEntity<byte[]> resp = restClient.get()
                    .uri(avatarUrl)
                    .retrieve()
                    .toEntity(byte[].class);
            byte[] bytes = resp.getBody();
            MediaType ct = resp.getHeaders().getContentType();
            String mime = ct != null ? ct.toString() : "image/jpeg";
            if (bytes != null && bytes.length > 0 && bytes.length <= MAX_AVATAR_BYTES
                    && mime.toLowerCase(java.util.Locale.ROOT).startsWith("image/")) {
                Optional<AvatarPayload> payload = Optional.of(new AvatarPayload(bytes, mime));
                avatarCache.put(account.getId(), payload);
                return payload;
            }
        } catch (Exception e) {
            log.debug("Không thể tải ảnh đại diện Google cho accountId={}: {}", account.getId(), e.getClass().getSimpleName());
        }

        avatarCache.put(account.getId(), Optional.empty());
        return Optional.empty();
    }

    private String renderHtmlResult(boolean success, String rawEmail, String message) {
        String title = success ? "Liên kết thành công" : "Liên kết thất bại";
        return renderHtmlResult(success, title, rawEmail, message);
    }

    private String renderHtmlResult(boolean success, String title, String rawEmail, String message) {
        String accent = success ? "#34d399" : "#fb7185";
        String accentBg = success ? "rgba(16, 185, 129, 0.16)" : "rgba(244, 63, 94, 0.16)";
        String accentBorder = success ? "rgba(52, 211, 153, 0.42)" : "rgba(251, 113, 133, 0.42)";
        String emailChipHtml = "";
        if (rawEmail != null && !rawEmail.isBlank()) {
            String masked = MailCredentials.maskEmail(rawEmail.trim());
            emailChipHtml = "        <div style=\"display:inline-block;margin:0 auto 14px auto;padding:6px 14px;border-radius:999px;"
                    + "background:rgba(148,163,184,0.14);border:1px solid rgba(148,163,184,0.32);font-size:13px;color:#e2e8f0;\">"
                    + "Email Google trả về: <strong>" + masked + "</strong></div>\n"
                    + "        <div style=\"font-size:12px;color:#94a3b8;margin-bottom:14px;\">"
                    + "Kiểm tra lại địa chỉ trên nếu bạn có nhiều tài khoản Google.</div>\n";
        }
        String statusSvg = success
                ? "<svg width=\"32\" height=\"32\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"" + accent + "\" stroke-width=\"2.6\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><polyline points=\"20 6 9 17 4 12\"/></svg>"
                : "<svg width=\"32\" height=\"32\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"" + accent + "\" stroke-width=\"2.6\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><line x1=\"18\" y1=\"6\" x2=\"6\" y2=\"18\"/><line x1=\"6\" y1=\"6\" x2=\"18\" y2=\"18\"/></svg>";

        return "<!DOCTYPE html>\n" +
                "<html lang=\"vi\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>" + title + " — Veyra Mail</title>\n" +
                "    <style>\n" +
                "        * { box-sizing: border-box; }\n" +
                "        body {\n" +
                "            font-family: 'Segoe UI', -apple-system, BlinkMacSystemFont, Roboto, Helvetica, Arial, sans-serif;\n" +
                "            display: flex;\n" +
                "            justify-content: center;\n" +
                "            align-items: center;\n" +
                "            min-height: 100vh;\n" +
                "            margin: 0;\n" +
                "            background: radial-gradient(circle at 82% 18%, rgba(244,63,94,0.22), transparent 44%),\n" +
                "                        radial-gradient(circle at 18% 82%, rgba(34,211,238,0.22), transparent 46%),\n" +
                "                        linear-gradient(135deg, #090c1f 0%, #16143a 52%, #0a2540 100%);\n" +
                "            color: #cbd5e1;\n" +
                "            padding: 20px;\n" +
                "        }\n" +
                "        .card {\n" +
                "            background: rgba(18, 22, 42, 0.82);\n" +
                "            backdrop-filter: blur(16px);\n" +
                "            -webkit-backdrop-filter: blur(16px);\n" +
                "            border: 1px solid rgba(148, 163, 184, 0.22);\n" +
                "            padding: 40px 44px;\n" +
                "            border-radius: 20px;\n" +
                "            box-shadow: 0 24px 60px rgba(0, 0, 0, 0.45);\n" +
                "            text-align: center;\n" +
                "            max-width: 480px;\n" +
                "            width: 100%;\n" +
                "        }\n" +
                "        .brand {\n" +
                "            display: inline-flex;\n" +
                "            align-items: center;\n" +
                "            gap: 11px;\n" +
                "            margin-bottom: 24px;\n" +
                "        }\n" +
                "        .wordmark {\n" +
                "            font-weight: 800;\n" +
                "            font-size: 20px;\n" +
                "            letter-spacing: 0.22em;\n" +
                "            color: #ffffff;\n" +
                "        }\n" +
                "        .tag {\n" +
                "            font-size: 10px;\n" +
                "            font-weight: 700;\n" +
                "            letter-spacing: 0.08em;\n" +
                "            color: #bae6fd;\n" +
                "            background: rgba(56, 189, 248, 0.16);\n" +
                "            border: 1px solid rgba(125, 211, 252, 0.4);\n" +
                "            padding: 3px 9px;\n" +
                "            border-radius: 999px;\n" +
                "        }\n" +
                "        .status-ring {\n" +
                "            width: 66px;\n" +
                "            height: 66px;\n" +
                "            margin: 0 auto 18px auto;\n" +
                "            border-radius: 50%;\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            justify-content: center;\n" +
                "            background: " + accentBg + ";\n" +
                "            border: 1.5px solid " + accentBorder + ";\n" +
                "        }\n" +
                "        h2 {\n" +
                "            color: #f8fafc;\n" +
                "            margin: 0 0 12px 0;\n" +
                "            font-size: 24px;\n" +
                "            font-weight: 700;\n" +
                "        }\n" +
                "        p {\n" +
                "            line-height: 1.65;\n" +
                "            font-size: 15px;\n" +
                "            color: #cbd5e1;\n" +
                "            margin: 0 0 24px 0;\n" +
                "        }\n" +
                "        p strong { color: #f8fafc; }\n" +
                "        .notice {\n" +
                "            background: rgba(56, 189, 248, 0.12);\n" +
                "            border: 1px solid rgba(125, 211, 252, 0.28);\n" +
                "            padding: 13px 18px;\n" +
                "            border-radius: 12px;\n" +
                "            font-size: 14px;\n" +
                "            font-weight: 500;\n" +
                "            color: #e0f2fe;\n" +
                "            line-height: 1.5;\n" +
                "        }\n" +
                "        .notice strong { color: #7dd3fc; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"card\">\n" +
                "        <div class=\"brand\">\n" +
                "            <svg width=\"42\" height=\"42\" viewBox=\"0 0 64 64\" xmlns=\"http://www.w3.org/2000/svg\" aria-label=\"Veyra Logo\">\n" +
                "                <defs>\n" +
                "                    <linearGradient id=\"vyBg\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\">\n" +
                "                        <stop offset=\"0%\" stop-color=\"#0e0a24\"/>\n" +
                "                        <stop offset=\"48%\" stop-color=\"#1e134a\"/>\n" +
                "                        <stop offset=\"100%\" stop-color=\"#0a304e\"/>\n" +
                "                    </linearGradient>\n" +
                "                    <linearGradient id=\"vyCyan\" x1=\"0\" y1=\"0.6\" x2=\"1\" y2=\"0.2\">\n" +
                "                        <stop offset=\"0%\" stop-color=\"#22d3ee\"/>\n" +
                "                        <stop offset=\"50%\" stop-color=\"#3b82f6\"/>\n" +
                "                        <stop offset=\"100%\" stop-color=\"#818cf8\"/>\n" +
                "                    </linearGradient>\n" +
                "                    <linearGradient id=\"vyCoral\" x1=\"0\" y1=\"0.5\" x2=\"1\" y2=\"0\">\n" +
                "                        <stop offset=\"0%\" stop-color=\"#f43f5e\"/>\n" +
                "                        <stop offset=\"48%\" stop-color=\"#fb7185\"/>\n" +
                "                        <stop offset=\"85%\" stop-color=\"#fbbf24\"/>\n" +
                "                        <stop offset=\"100%\" stop-color=\"#fef3c7\"/>\n" +
                "                    </linearGradient>\n" +
                "                    <linearGradient id=\"vyPearl\" x1=\"1\" y1=\"0\" x2=\"0\" y2=\"1\">\n" +
                "                        <stop offset=\"0%\" stop-color=\"#ffffff\"/>\n" +
                "                        <stop offset=\"60%\" stop-color=\"#f0f9ff\"/>\n" +
                "                        <stop offset=\"100%\" stop-color=\"#bae6fd\"/>\n" +
                "                    </linearGradient>\n" +
                "                    <linearGradient id=\"vyRim\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\">\n" +
                "                        <stop offset=\"0%\" stop-color=\"rgba(255,255,255,0.55)\"/>\n" +
                "                        <stop offset=\"50%\" stop-color=\"rgba(244,114,182,0.35)\"/>\n" +
                "                        <stop offset=\"100%\" stop-color=\"rgba(34,211,238,0.5)\"/>\n" +
                "                    </linearGradient>\n" +
                "                </defs>\n" +
                "                <rect x=\"2.5\" y=\"2.5\" width=\"59\" height=\"59\" rx=\"18\" fill=\"url(#vyBg)\" stroke=\"url(#vyRim)\" stroke-width=\"1.3\"/>\n" +
                "                <path d=\"M11 31.5 C17 43 24.5 51.5 31.5 52 C39 52.4 46.5 36.5 53.5 19.5 C44.5 31 36.8 41.2 30.8 41 C24.6 40.8 19 34 11 31.5 Z\" fill=\"url(#vyCyan)\"/>\n" +
                "                <path d=\"M10.5 21 C19.5 18 29 22.8 34.8 31.8 C41.2 24.5 47.2 18.2 54 13.8 C48 24.8 41.8 34.6 33.4 41.8 C25.2 34.2 19 26.8 10.5 21 Z\" fill=\"url(#vyCoral)\"/>\n" +
                "                <path d=\"M19.2 23.2 C26.5 25.4 30.8 30.8 32.6 36.4 C39.4 29.8 46.2 21.8 54 13.8 C47.6 26.8 40.4 37.6 32 45.2 C27.2 38.4 23.8 30.6 19.2 23.2 Z\" fill=\"url(#vyPearl)\"/>\n" +
                "                <path d=\"M31.5 11.4 Q32.2 15.8 36.6 16.5 Q32.2 17.2 31.5 21.6 Q30.8 17.2 26.4 16.5 Q30.8 15.8 31.5 11.4 Z\" fill=\"#fde047\"/>\n" +
                "                <circle cx=\"21.5\" cy=\"14.3\" r=\"1.4\" fill=\"#a5f3fc\"/>\n" +
                "            </svg>\n" +
                "            <span class=\"wordmark\">VEYRA</span>\n" +
                "            <span class=\"tag\">SECURE MAIL</span>\n" +
                "        </div>\n" +
                "        <div class=\"status-ring\">" + statusSvg + "</div>\n" +
                "        <h2>" + title + "</h2>\n" +
                emailChipHtml +
                "        <p>" + message + "</p>\n" +
                "        <div class=\"notice\">Bạn có thể đóng cửa sổ trình duyệt này và quay lại ứng dụng <strong>Veyra Mail</strong>.</div>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
    }
}

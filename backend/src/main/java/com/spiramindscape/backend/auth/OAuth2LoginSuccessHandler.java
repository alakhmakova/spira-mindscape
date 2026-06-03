package com.spiramindscape.backend.auth;

import com.spiramindscape.backend.ai.crypto.EncryptionService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Runs after a successful Google login. Captures the OAuth <em>refresh token</em>
 * (Google issues one because the authorization request asks for
 * {@code access_type=offline}), stores it encrypted on the {@link AppUser}, then
 * redirects the browser to the SPA.
 *
 * <p>The refresh token lets the backend mint fresh Drive access tokens later
 * (see {@code GoogleDriveService}) without forcing the user to log in again.
 */
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final AppUserRepository appUserRepository;
    private final EncryptionService encryptionService;

    public OAuth2LoginSuccessHandler(OAuth2AuthorizedClientService authorizedClientService,
                                     AppUserRepository appUserRepository,
                                     EncryptionService encryptionService,
                                     @Value("${app.frontend.url}") String frontendUrl) {
        this.authorizedClientService = authorizedClientService;
        this.appUserRepository = appUserRepository;
        this.encryptionService = encryptionService;
        setDefaultTargetUrl(frontendUrl);
        setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        captureRefreshToken(authentication);
        super.onAuthenticationSuccess(request, response, authentication);
    }

    private void captureRefreshToken(Authentication authentication) {
        try {
            if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)
                    || !(authentication.getPrincipal() instanceof AppUserOidcUser principal)) {
                return;
            }
            OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                    oauthToken.getAuthorizedClientRegistrationId(), authentication.getName());
            if (client == null) {
                return;
            }
            OAuth2RefreshToken refreshToken = client.getRefreshToken();
            if (refreshToken == null) {
                // Google only returns a refresh token with access_type=offline + consent.
                // If it's absent on this login, keep any previously stored token.
                return;
            }
            AppUser user = principal.getAppUser();
            user.setEncRefreshToken(encryptionService.encrypt(refreshToken.getTokenValue()));
            appUserRepository.save(user);
        } catch (Exception e) {
            // Never block login because refresh-token capture failed.
            log.warn("Could not capture Google refresh token: {}", e.getMessage());
        }
    }
}

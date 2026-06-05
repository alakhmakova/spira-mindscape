package com.spiramindscape.backend.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * Custom OIDC user service invoked by Spring Security after a successful Google login.
 *
 * <p>Delegates to the standard {@link OidcUserService} for token validation, then
 * resolves (or creates) the {@link AppUser} database row and wraps both together in
 * an {@link AppUserOidcUser} — which becomes the principal stored in the session.
 */
@Service
@RequiredArgsConstructor
public class AppUserOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final AppUserService appUserService;
    private final OidcUserService delegate = new OidcUserService();

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = delegate.loadUser(userRequest);
        AppUser appUser = appUserService.findOrCreateFromOidc(oidcUser);
        return new AppUserOidcUser(oidcUser, appUser);
    }
}

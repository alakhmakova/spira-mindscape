package com.spiramindscape.backend.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

/**
 * Spring Security principal that combines the Google {@link OidcUser} (for OIDC plumbing)
 * with the resolved {@link AppUser} database entity (for business logic).
 *
 * <p>Stored in the session by Spring Security after a successful OAuth2 login.
 * Retrieved via {@link CurrentUserProvider} in every authenticated request.
 *
 * <p>Must stay {@link Serializable}: sessions are persisted to PostgreSQL by
 * spring-session-jdbc, which JDK-serializes the whole SecurityContext. A
 * non-serializable principal makes every login fail with a 500.
 */
public class AppUserOidcUser implements OidcUser, Serializable {

    private static final long serialVersionUID = 1L;

    private final OidcUser delegate;
    private final AppUser appUser;

    public AppUserOidcUser(OidcUser delegate, AppUser appUser) {
        this.delegate = delegate;
        this.appUser = appUser;
    }

    /** The resolved database user — use this for all business logic. */
    public AppUser getAppUser() {
        return appUser;
    }

    // ---- OidcUser delegation ----

    @Override
    public Map<String, Object> getClaims() {
        return delegate.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return delegate.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
        return delegate.getIdToken();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return delegate.getAuthorities();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }
}

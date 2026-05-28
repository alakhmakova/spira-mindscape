package com.spiramindscape.backend.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Reads the currently authenticated {@link AppUser} from the Spring Security context.
 *
 * <p>The security context is populated by Spring Security after a successful OAuth2/OIDC
 * login: the principal is an {@link AppUserOidcUser} which carries both the OIDC token
 * and the resolved database user.
 *
 * <p>In tests the security context is populated manually (see {@code BaseGraphQlIntegrationTest}).
 */
@Component
public class CurrentUserProvider {

    /**
     * Returns the authenticated {@link AppUser}.
     *
     * @throws IllegalStateException if there is no authenticated principal in the context
     *                               (should not happen for endpoints secured by Spring Security)
     */
    public AppUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserOidcUser appUserOidcUser) {
            return appUserOidcUser.getAppUser();
        }
        throw new IllegalStateException("No authenticated AppUser in security context");
    }
}

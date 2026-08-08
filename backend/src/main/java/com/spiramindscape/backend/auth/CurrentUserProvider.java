package com.spiramindscape.backend.auth;

import com.spiramindscape.backend.logging.RequestLogContextFilter;
import org.slf4j.MDC;
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
            AppUser user = appUserOidcUser.getAppUser();
            // Tag the rest of this request's log lines with the user, so an error can be
            // traced to whose data it happened on. This is the one place the principal is
            // resolved, so one line here covers every endpoint. The numeric id — never the
            // email — is what we log; RequestLogContextFilter clears it after the request.
            MDC.put(RequestLogContextFilter.USER_ID, String.valueOf(user.getId()));
            return user;
        }
        throw new IllegalStateException("No authenticated AppUser in security context");
    }
}

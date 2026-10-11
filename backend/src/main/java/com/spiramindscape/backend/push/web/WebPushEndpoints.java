package com.spiramindscape.backend.push.web;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * Which push endpoints the server will POST to. A subscription's endpoint is a URL the
 * <b>client</b> hands us, and the dispatcher later sends a request to it from inside Google's
 * network — so an unchecked endpoint would make this app an SSRF primitive (aimed at the metadata
 * server, say, or anything internal). Only https on the push services the browsers actually use.
 */
public final class WebPushEndpoints {

    /** Exact hosts, and the domains whose subdomains are allowed. */
    private static final List<String> HOSTS = List.of("fcm.googleapis.com");
    private static final List<String> DOMAINS = List.of(
            ".push.services.mozilla.com",
            ".push.apple.com",
            ".notify.windows.com");

    public static final int MAX_LENGTH = 1000;

    private WebPushEndpoints() {
    }

    public static boolean isAllowed(String endpoint) {
        if (endpoint == null || endpoint.isBlank() || endpoint.length() > MAX_LENGTH) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(endpoint.trim());
        } catch (Exception e) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getRawUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getHost() == null) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return HOSTS.contains(host) || DOMAINS.stream().anyMatch(host::endsWith);
    }
}

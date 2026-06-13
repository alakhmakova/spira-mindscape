package com.spiramindscape.backend.ai.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fetches a web page and returns its readable text, for the AI's {@code read_url}
 * tool. No API key needed — a plain HTTP GET plus HTML-to-text stripping.
 *
 * <p>Best-effort: pages behind login or rendered by JavaScript yield little
 * text, in which case the caller (the model) is told to ask the user to paste
 * the content. Includes a basic SSRF guard against internal/loopback hosts.
 */
@Service
public class UrlReadService {

    private static final Logger log = LoggerFactory.getLogger(UrlReadService.class);

    private static final int MAX_CHARS = 12_000;
    private static final int MAX_BODY_CHARS = 2_000_000;

    private static final int MAX_REDIRECT_HOPS = 3;

    // SSRF: do NOT let the HTTP client auto-follow redirects — a public URL can
    // 302 to an internal/metadata address, bypassing the pre-fetch host check.
    // We follow manually, re-validating the host on every hop.
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /** Returns the page's readable text, or a short explanatory message (never throws). */
    public String read(String url) {
        if (url == null || url.isBlank()) return "No URL was provided.";
        String current = url.trim();
        try {
            HttpResponse<String> res = null;
            for (int hop = 0; hop <= MAX_REDIRECT_HOPS; hop++) {
                String rejection = validateFetchUrl(current);
                if (rejection != null) return rejection;
                HttpRequest req = HttpRequest.newBuilder(URI.create(current))
                        .timeout(Duration.ofSeconds(20))
                        .header("user-agent", "Mozilla/5.0 (compatible; SpiraBot/1.0)")
                        .header("accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.5")
                        .GET()
                        .build();
                res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                int sc = res.statusCode();
                if (sc >= 300 && sc < 400) {
                    String location = res.headers().firstValue("location").orElse(null);
                    if (location == null) break;
                    // Resolve relative redirects against the current URL, then re-check.
                    current = URI.create(current).resolve(location).toString();
                    if (hop == MAX_REDIRECT_HOPS) {
                        return "That page redirected too many times — ask the user to paste the text.";
                    }
                    continue;
                }
                break;
            }
            if (res == null) return "That doesn't look like a valid URL.";
            if (res.statusCode() >= 400) {
                return "The page returned HTTP " + res.statusCode()
                        + " — it may require login or be unavailable. Ask the user to paste the text.";
            }

            String contentType = res.headers().firstValue("content-type").orElse("").toLowerCase();
            String body = res.body();
            if (body == null || body.isBlank()) return "(the page returned no content)";
            if (body.length() > MAX_BODY_CHARS) body = body.substring(0, MAX_BODY_CHARS);

            String text = (contentType.contains("html") || body.contains("<html") || body.contains("<body"))
                    ? htmlToText(body)
                    : body.strip();

            if (text.isBlank()) {
                return "(no readable text — the page is likely login-protected or rendered by JavaScript; "
                        + "ask the user to paste the text)";
            }
            return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) + "…[truncated]" : text;
        } catch (Exception e) {
            log.warn("read_url failed for {}: {}", url, e.getMessage());
            return "Couldn't fetch the page (" + e.getMessage() + "). Ask the user to paste the text.";
        }
    }

    /**
     * Validates a single URL to fetch (called on every redirect hop). Returns a
     * user-facing rejection message, or {@code null} if the URL is safe to fetch.
     *
     * <p>SSRF guard: scheme must be http(s); port must be 80/443 (or default);
     * the host must not be loopback/private/link-local or the cloud metadata
     * endpoint; resolution is checked here, immediately before the request, to
     * shrink (not eliminate) the DNS-rebinding window.
     */
    private static String validateFetchUrl(String u) {
        if (!(u.startsWith("http://") || u.startsWith("https://"))) {
            return "Only http(s) URLs can be read.";
        }
        URI uri;
        try {
            uri = URI.create(u);
        } catch (Exception e) {
            return "That doesn't look like a valid URL.";
        }
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) {
            return "That address can't be fetched (only standard web ports are allowed).";
        }
        if (isBlockedHost(uri.getHost())) {
            return "That address can't be fetched (internal/local addresses are not allowed).";
        }
        return null;
    }

    /** Cloud metadata hostnames that must never be fetched (credential theft). */
    private static final java.util.Set<String> BLOCKED_HOSTNAMES = java.util.Set.of(
            "localhost", "metadata.google.internal", "metadata");

    /** Blocks loopback / private / link-local / metadata hosts to avoid SSRF. */
    private static boolean isBlockedHost(String host) {
        if (host == null || host.isBlank()) return true;
        String h = host.toLowerCase();
        if (BLOCKED_HOSTNAMES.contains(h) || h.endsWith(".local") || h.endsWith(".internal")) return true;
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()
                        || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()
                        || addr.isMulticastAddress()) {
                    return true;
                }
                // GCP/AWS metadata IPs (link-local 169.254/16 is caught above,
                // but block the canonical addresses explicitly for clarity).
                String ip = addr.getHostAddress();
                if (ip.equals("169.254.169.254") || ip.startsWith("fd00:ec2:")) return true;
            }
        } catch (Exception e) {
            return true; // can't resolve → don't fetch
        }
        return false;
    }

    /** Crude but dependency-free HTML → plain text. */
    private static String htmlToText(String html) {
        String s = html
                .replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?is)<noscript.*?</noscript>", " ")
                .replaceAll("(?is)<!--.*?-->", " ")
                .replaceAll("(?i)<(br|/p|/div|/h[1-6]|/li|/tr|/section|/article)\\s*/?>", "\n")
                .replaceAll("(?s)<[^>]+>", " ");
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
             .replace("&gt;", ">").replace("&#39;", "'").replace("&quot;", "\"");
        s = s.replaceAll("[ \\t]+", " ").replaceAll(" *\\n *", "\n").replaceAll("\\n{3,}", "\n\n").trim();
        return s;
    }
}

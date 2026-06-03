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

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Returns the page's readable text, or a short explanatory message (never throws). */
    public String read(String url) {
        if (url == null || url.isBlank()) return "No URL was provided.";
        String u = url.trim();
        if (!(u.startsWith("http://") || u.startsWith("https://"))) {
            return "Only http(s) URLs can be read.";
        }
        URI uri;
        try {
            uri = URI.create(u);
        } catch (Exception e) {
            return "That doesn't look like a valid URL.";
        }
        if (isBlockedHost(uri.getHost())) {
            return "That address can't be fetched (internal/local addresses are not allowed).";
        }

        try {
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(20))
                    .header("user-agent", "Mozilla/5.0 (compatible; SpiraBot/1.0)")
                    .header("accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.5")
                    .GET()
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
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
            log.warn("read_url failed for {}: {}", u, e.getMessage());
            return "Couldn't fetch the page (" + e.getMessage() + "). Ask the user to paste the text.";
        }
    }

    /** Blocks loopback / private / link-local hosts to avoid SSRF into the internal network. */
    private static boolean isBlockedHost(String host) {
        if (host == null || host.isBlank()) return true;
        String h = host.toLowerCase();
        if (h.equals("localhost") || h.endsWith(".local") || h.endsWith(".internal")) return true;
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()
                        || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()) {
                    return true;
                }
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

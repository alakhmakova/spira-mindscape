package com.spiramindscape.backend.push.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * {@link WebPushSender} over plain HTTP: the RFC 8291 body, a VAPID {@code Authorization} header,
 * a TTL of an hour (a reminder an hour late is still worth showing; the "decide now" page covers
 * the rest) and {@code Urgency: high}, which lets an Android phone wake for it.
 */
@Component
public class VapidWebPushSender implements WebPushSender {

    private static final Logger log = LoggerFactory.getLogger(VapidWebPushSender.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebPushProperties properties;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public VapidWebPushSender(WebPushProperties properties) {
        this.properties = properties;
    }

    @Override
    public Result send(WebPushSubscription subscription, byte[] payload) {
        if (!properties.enabled()) {
            return Result.FAILED;
        }
        // Checked again here, not only at subscribe time: a row that predates a tighter list
        // must not be sent to either.
        if (!WebPushEndpoints.isAllowed(subscription.getEndpoint())) {
            return Result.GONE;
        }
        try {
            URI endpoint = URI.create(subscription.getEndpoint());
            byte[] body = WebPushCrypto.encrypt(payload,
                    WebPushCrypto.decode(subscription.getP256dh()),
                    WebPushCrypto.decode(subscription.getAuth()));
            String audience = endpoint.getScheme() + "://" + endpoint.getHost();
            String jwt = WebPushCrypto.vapidJwt(audience, properties.subject(),
                    Instant.now().plus(Duration.ofHours(12)).getEpochSecond(), properties.signingKey());

            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/octet-stream")
                    .header("Content-Encoding", "aes128gcm")
                    .header("TTL", "3600")
                    .header("Urgency", "high")
                    .header("Authorization", "vapid t=" + jwt + ", k=" + properties.publicKey())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 404 || status == 410) {
                return Result.GONE;
            }
            if (status >= 200 && status < 300) {
                return Result.SENT;
            }
            // The endpoint is a capability URL — log its host only, never the whole of it.
            log.warn("web_push_rejected status={} host={}", status, endpoint.getHost());
            return Result.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.FAILED;
        } catch (Exception e) {
            log.warn("web_push_failed subscriptionId={}", subscription.getId(), e);
            return Result.FAILED;
        }
    }
}

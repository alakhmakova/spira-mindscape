package com.spiramindscape.backend.push.web;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The SSRF boundary: the server only ever POSTs to https on the browsers' own push services. */
class WebPushEndpointsTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://fcm.googleapis.com/fcm/send/abc:def",
            "https://updates.push.services.mozilla.com/wpush/v2/gAAAA",
            "https://web.push.apple.com/QGuQ-abc",
            "https://wns2-par02p.notify.windows.com/w/?token=BQYAAA",
            "https://fcm.googleapis.com:443/fcm/send/x",
    })
    void acceptsThePushServices(String endpoint) {
        assertThat(WebPushEndpoints.isAllowed(endpoint)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://fcm.googleapis.com/fcm/send/abc",           // not https
            "http://169.254.169.254/computeMetadata/v1/",       // the metadata server
            "https://169.254.169.254/",
            "https://internal.example/fcm/send",
            "https://fcm.googleapis.com.evil.example/x",         // suffix trick
            "https://evilpush.apple.com/x",                      // not a subdomain of push.apple.com
            "https://user:pass@fcm.googleapis.com/x",            // userinfo
            "https://fcm.googleapis.com:8443/x",                 // another port
            "file:///etc/passwd",
            "not a url",
            "",
    })
    void refusesEverythingElse(String endpoint) {
        assertThat(WebPushEndpoints.isAllowed(endpoint)).isFalse();
    }
}

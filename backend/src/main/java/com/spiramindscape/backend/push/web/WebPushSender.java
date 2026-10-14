package com.spiramindscape.backend.push.web;

/** Sends one encrypted message to one subscription. An interface so the dispatcher's tests need no network. */
public interface WebPushSender {

    enum Result {
        /** Accepted by the push service. */
        SENT,
        /** The subscription is gone (404 / 410) — the browser unsubscribed or expired it. */
        GONE,
        /** Anything else; the subscription is kept and the next reminder tries again. */
        FAILED
    }

    Result send(WebPushSubscription subscription, byte[] payload);
}

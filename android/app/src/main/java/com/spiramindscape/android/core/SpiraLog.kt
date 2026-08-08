package com.spiramindscape.android.core

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Logging for the Android app: one call writes to logcat **and** records a non-fatal in
 * Crashlytics.
 *
 * Crashlytics already captures crashes automatically, but the app's real problem was the
 * opposite: roughly thirty `catch` blocks that recovered silently, so a failed save or a
 * dropped AI transcript produced no signal at all — not on the device, not in the console.
 * `recordException` is what turns those into something visible from a tester's phone.
 *
 * Lives in `core` rather than `ui/util` because `data/` calls it too, and a `data → ui`
 * dependency is backwards.
 *
 * ## Safe when Firebase is absent
 *
 * The Crashlytics *dependency* is unconditional, but its Gradle *plugins* are only applied
 * when `google-services.json` exists (CI and a fresh clone have neither). Without it
 * `FirebaseApp` is never initialized and `getInstance()` throws — as it also does under
 * Robolectric. So every call here is guarded, the failure is cached rather than re-thrown
 * on each call, and `Log` runs first so logcat works regardless. A logging call must never
 * be the thing that crashes the app.
 */
object SpiraLog {

    @Volatile
    private var crashlyticsUnavailable = false

    private fun crashlytics(): FirebaseCrashlytics? {
        if (crashlyticsUnavailable) return null
        return try {
            FirebaseCrashlytics.getInstance()
        } catch (t: Throwable) {
            crashlyticsUnavailable = true
            null
        }
    }

    /** A recoverable failure: the app carried on, but something the user wanted didn't happen. */
    fun w(tag: String, message: String, e: Throwable? = null) {
        logcat { Log.w(tag, message, e) }
        record(message, e)
    }

    /** A failure the user is likely to notice. */
    fun e(tag: String, message: String, e: Throwable? = null) {
        logcat { Log.e(tag, message, e) }
        record(message, e)
    }

    /**
     * `android.util.Log` is a stub on a plain JVM and throws "not mocked", so an ordinary
     * (non-Robolectric) unit test would fail the moment production code logged something —
     * turning this helper into the thing that breaks the build. Robolectric and real devices
     * are unaffected; the call goes through as normal.
     */
    private inline fun logcat(write: () -> Unit) {
        try {
            write()
        } catch (t: Throwable) {
            // Nothing to fall back to, and nothing worth propagating.
        }
    }

    /**
     * A breadcrumb shown alongside the next crash report. Use for context ("opened goal"),
     * never for the user's own text.
     */
    fun breadcrumb(message: String) {
        crashlytics()?.log(message)
    }

    /**
     * Attributes crash reports to a user. Takes the backend's numeric id — an opaque
     * surrogate key that means nothing outside our database. Never pass an email or a name.
     */
    fun setUserId(id: Long?) {
        crashlytics()?.setUserId(id?.toString() ?: "")
    }

    private fun record(message: String, e: Throwable?) {
        val crashlytics = crashlytics() ?: return
        try {
            crashlytics.log(message)
            crashlytics.recordException(e ?: RuntimeException(message))
        } catch (t: Throwable) {
            crashlyticsUnavailable = true
        }
    }
}

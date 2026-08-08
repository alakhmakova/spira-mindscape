package com.spiramindscape.android.core

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

/**
 * The contract worth testing here is not "does it log" but "can it ever break the app".
 *
 * Robolectric reproduces the exact environment that makes that a real risk: no
 * `google-services.json`, so `FirebaseApp` is never initialized and
 * `FirebaseCrashlytics.getInstance()` throws. That is also CI's environment and a fresh
 * clone's. A logging helper that propagated that exception would turn every recovered
 * failure into a crash — the opposite of what it exists for.
 */
@RunWith(RobolectricTestRunner::class)
class SpiraLogTest {

    @Test
    fun `w does not throw when Firebase is unavailable`() {
        SpiraLog.w("Test", "something_failed", RuntimeException("boom"))
    }

    @Test
    fun `w without a throwable does not throw`() {
        SpiraLog.w("Test", "something_failed")
    }

    @Test
    fun `e does not throw when Firebase is unavailable`() {
        SpiraLog.e("Test", "something_broke", IllegalStateException("bad"))
    }

    @Test
    fun `setUserId and breadcrumb do not throw when Firebase is unavailable`() {
        SpiraLog.setUserId(42L)
        SpiraLog.setUserId(null)
        SpiraLog.breadcrumb("opened a goal")
    }

    @Test
    fun `logcat still receives the message when Crashlytics cannot`() {
        // The fallback that matters: on a developer's device `adb logcat` is the diagnostic
        // path named in CLAUDE.md, and it must work whether or not Firebase is configured.
        ShadowLog.clear()

        SpiraLog.w("SpiraLogTest", "fcm_registration_skipped", RuntimeException("no play services"))

        val logged = ShadowLog.getLogs().any {
            it.tag == "SpiraLogTest" && it.msg.contains("fcm_registration_skipped")
        }
        assertTrue("expected the warning to reach logcat", logged)
    }

    @Test
    fun `repeated calls stay cheap after Crashlytics is found to be unavailable`() {
        // The unavailability is cached rather than re-discovered, so a failing loop doesn't
        // pay for a thrown-and-caught exception on every iteration.
        val startedAt = System.nanoTime()
        repeat(500) { SpiraLog.w("Test", "repeated_failure", RuntimeException("boom")) }
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

        assertTrue("500 log calls took ${elapsedMillis}ms — the guard is not caching",
            elapsedMillis < 2_000)
    }
}

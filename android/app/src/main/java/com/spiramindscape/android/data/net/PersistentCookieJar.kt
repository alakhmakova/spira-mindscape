package com.spiramindscape.android.data.net

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Minimal cookie jar backed by SharedPreferences so the backend cookies — the `SESSION` cookie
 * and the readable `XSRF-TOKEN` — survive app restarts. The app then reuses the same
 * server-side session on every request, exactly like a browser keeping its cookies.
 *
 * We only ever talk to one backend host, so persisted cookies are re-parsed against that single
 * base URL. Cookies are keyed by name (the backend uses distinct names: `SESSION`, `XSRF-TOKEN`).
 */
class PersistentCookieJar(
    context: Context,
    private val baseUrl: HttpUrl,
) : CookieJar {

    private val prefs = context.applicationContext
        .getSharedPreferences("spira_cookies", Context.MODE_PRIVATE)

    private val store = mutableMapOf<String, Cookie>()

    init {
        prefs.all.forEach { (_, raw) ->
            if (raw is String) {
                Cookie.parse(baseUrl, raw)?.let { store[it.name] = it }
            }
        }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val editor = prefs.edit()
        for (cookie in cookies) {
            store[cookie.name] = cookie
            editor.putString(cookie.name, cookie.toString())
        }
        editor.apply()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return store.values.filter { it.expiresAt > now && it.matches(url) }
    }

    /** Read a cookie value by name (used to echo XSRF-TOKEN as the X-XSRF-TOKEN header). */
    @Synchronized
    fun value(name: String): String? = store[name]?.value

    /** Drop all cookies — call on logout so the next launch starts anonymous. */
    @Synchronized
    fun clear() {
        store.clear()
        prefs.edit().clear().apply()
    }
}

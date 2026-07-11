package com.khaata.app.security

import android.content.Context

/**
 * Per-device flag for the optional app-open lock. When on, MainActivity shows the
 * SecurityGateScreen (phone PIN / pattern / biometrics) before the ledger. When off
 * — the default — the gate is skipped entirely, since the app already requires an
 * email/password sign-in and the device has its own lock screen.
 *
 * Local and synchronous (SharedPreferences) on purpose: MainActivity needs the value
 * the instant it decides which screen to show, with no network round-trip. A lock
 * preference is also inherently device-local — you wouldn't want enabling it on one
 * phone to lock you out on another.
 */
private const val PREFS_NAME = "khaata_security"
private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"

/** Change this to `true` to make the app-open lock on by default (privacy-first). */
private const val APP_LOCK_DEFAULT = false

private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

/** True when the user has opted into unlocking Khaata on open. */
fun isAppLockEnabled(context: Context): Boolean =
    prefs(context).getBoolean(KEY_APP_LOCK_ENABLED, APP_LOCK_DEFAULT)

fun setAppLockEnabled(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
}

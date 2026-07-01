package com.khaata.app.onboarding

import android.content.Context

/**
 * Tiny local (per-device) store for "have we shown this person the ropes yet".
 *
 * Deliberately NOT synced to Firestore: onboarding/tutorial state is a
 * UI-nicety, not financial data, and we don't want a slow network round
 * trip blocking the very first screen a new user sees. If they reinstall
 * or switch devices, they simply see onboarding again — that's fine.
 */
private const val PREFS_NAME = "khaata_onboarding"
private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
private const val KEY_TUTORIAL_SEEN_PREFIX = "tutorial_seen_"

private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

/** True once the user has finished (or explicitly skipped) the setup wizard. */
fun isOnboardingComplete(context: Context): Boolean =
    prefs(context).getBoolean(KEY_ONBOARDING_COMPLETE, false)

fun setOnboardingComplete(context: Context, complete: Boolean = true) {
    prefs(context).edit().putBoolean(KEY_ONBOARDING_COMPLETE, complete).apply()
}

/**
 * Per-screen tutorial flag, keyed by a stable id (e.g. "dashboard", "budgets").
 * Lets each tab show its own contextual walkthrough exactly once, the first
 * time the user lands on it, without re-showing it on every app open.
 */
fun hasTutorialBeenSeen(context: Context, screenId: String): Boolean =
    prefs(context).getBoolean(KEY_TUTORIAL_SEEN_PREFIX + screenId, false)

fun markTutorialSeen(context: Context, screenId: String) {
    prefs(context).edit().putBoolean(KEY_TUTORIAL_SEEN_PREFIX + screenId, true).apply()
}

/** Used by the "Replay tutorial" action in Settings — resets everything. */
fun resetAllTutorials(context: Context) {
    val editor = prefs(context).edit()
    listOf("dashboard", "budgets", "goals", "add_entry", "analytics", "history").forEach {
        editor.putBoolean(KEY_TUTORIAL_SEEN_PREFIX + it, false)
    }
    editor.apply()
}
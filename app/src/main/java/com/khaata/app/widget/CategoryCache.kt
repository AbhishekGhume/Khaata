package com.khaata.app.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.firebase.auth.FirebaseAuth
import com.khaata.app.util.CategoryMeta
import com.khaata.app.util.DEFAULT_CATEGORIES
import org.json.JSONArray
import org.json.JSONObject

/**
 * Device-local mirror of the signed-in user's category list, for the surfaces that
 * render outside the app's live Firestore session: the home-screen widget and the
 * quick-add popup. Both need categories synchronously (a widget can't hold a
 * snapshot listener open), so [com.khaata.app.KhaataApp] writes every emission of
 * the categories flow here and the widget re-renders when the cached copy changed.
 *
 * Falls back to [DEFAULT_CATEGORIES] until the first save lands, mirroring the
 * repository's own empty-collection fallback.
 */
object CategoryCache {

    private const val PREFS_NAME = "khaata_category_cache"
    private const val KEY_JSON = "categories_json"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Cache entries are keyed per-uid: the widget and quick-add popup read this
    // outside the app's auth-gated session, so after an account switch an unkeyed
    // cache would keep serving the previous user's categories until the app is
    // next opened. No signed-in user → no cached data.
    private fun jsonKey(): String? =
        FirebaseAuth.getInstance().currentUser?.uid?.let { "${KEY_JSON}_$it" }

    /** Persists the list. Returns true when it differed from the cached copy (i.e. the widget needs a refresh). */
    fun save(context: Context, categories: List<CategoryMeta>): Boolean {
        val key = jsonKey() ?: return false
        val json = JSONArray().apply {
            categories.forEach { c ->
                put(
                    JSONObject()
                        .put("key", c.key)
                        .put("label", c.label)
                        .put("colorArgb", c.color.toArgb().toLong())
                        .put("iconKey", c.iconKey)
                )
            }
        }.toString()
        val stored = prefs(context).getString(key, null)
        if (stored == json) return false
        prefs(context).edit().putString(key, json).apply()
        return true
    }

    fun load(context: Context): List<CategoryMeta> {
        val key = jsonKey() ?: return DEFAULT_CATEGORIES
        val raw = prefs(context).getString(key, null) ?: return DEFAULT_CATEGORIES
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CategoryMeta(
                    key = o.getString("key"),
                    label = o.getString("label"),
                    color = Color(o.getLong("colorArgb").toInt()),
                    iconKey = o.optString("iconKey", "category")
                )
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: DEFAULT_CATEGORIES
    }
}

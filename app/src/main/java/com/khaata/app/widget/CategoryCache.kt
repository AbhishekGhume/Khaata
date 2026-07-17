package com.khaata.app.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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

    /** Persists the list. Returns true when it differed from the cached copy (i.e. the widget needs a refresh). */
    fun save(context: Context, categories: List<CategoryMeta>): Boolean {
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
        val stored = prefs(context).getString(KEY_JSON, null)
        if (stored == json) return false
        prefs(context).edit().putString(KEY_JSON, json).apply()
        return true
    }

    fun load(context: Context): List<CategoryMeta> {
        val raw = prefs(context).getString(KEY_JSON, null) ?: return DEFAULT_CATEGORIES
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

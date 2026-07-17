package com.khaata.app.widget

import android.content.Context
import com.khaata.app.data.model.Template
import org.json.JSONArray
import org.json.JSONObject

/**
 * Device-local mirror of the signed-in user's quick-add templates, for the
 * quick-add popup — the same pattern as [CategoryCache]: the popup renders
 * outside the app's live Firestore session and needs its data synchronously,
 * so [com.khaata.app.MainActivity] writes every emission of the templates flow
 * here and the popup reads the cached copy.
 *
 * Empty until the first save lands (a user with no templates simply sees no
 * chip row).
 */
object TemplateCache {

    private const val PREFS_NAME = "khaata_template_cache"
    private const val KEY_JSON = "templates_json"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Persists the list. Returns true when it differed from the cached copy. */
    fun save(context: Context, templates: List<Template>): Boolean {
        val json = JSONArray().apply {
            templates.forEach { t ->
                put(
                    JSONObject()
                        .put("label", t.label)
                        .put("category", t.category)
                        .put("amount", t.amount)
                        .put("note", t.note)
                )
            }
        }.toString()
        val stored = prefs(context).getString(KEY_JSON, null)
        if (stored == json) return false
        prefs(context).edit().putString(KEY_JSON, json).apply()
        return true
    }

    fun load(context: Context): List<Template> {
        val raw = prefs(context).getString(KEY_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Template(
                    label = o.getString("label"),
                    category = o.getString("category"),
                    amount = o.getDouble("amount"),
                    note = o.optString("note", "")
                )
            }
        }.getOrNull() ?: emptyList()
    }
}

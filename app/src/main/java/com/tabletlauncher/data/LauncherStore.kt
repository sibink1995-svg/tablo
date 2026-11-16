package com.tabletlauncher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small SharedPreferences + JSON persistence layer. */
class LauncherStore(context: Context) {

    private val prefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)

    private fun parseArray(s: String?): JSONArray? =
        try { s?.let { JSONArray(it) } } catch (e: Exception) { null }

    private fun parseObject(s: String?): JSONObject? =
        try { s?.let { JSONObject(it) } } catch (e: Exception) { null }

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    // ---- Pins ----
    fun loadPins(): List<String> = parseArray(prefs.getString("pins", null))?.strings() ?: emptyList()

    fun savePins(pins: List<String>) {
        prefs.edit().putString("pins", JSONArray(pins).toString()).apply()
    }

    // ---- Folders ----
    fun loadFolders(): List<Folder> {
        val arr = parseArray(prefs.getString("folders", null)) ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Folder(o.getString("id"), o.getString("name"), o.getJSONArray("apps").strings())
        }
    }

    fun saveFolders(folders: List<Folder>) {
        val arr = JSONArray()
        folders.forEach { f ->
            arr.put(
                JSONObject()
                    .put("id", f.id)
                    .put("name", f.name)
                    .put("apps", JSONArray(f.appKeys))
            )
        }
        prefs.edit().putString("folders", arr.toString()).apply()
    }

    // ---- Usage (for suggestions) ----
    fun loadUsage(): Map<String, UsageStat> {
        val o = parseObject(prefs.getString("usage", null)) ?: return emptyMap()
        val out = HashMap<String, UsageStat>()
        o.keys().forEach { k ->
            val v = o.getJSONObject(k)
            out[k] = UsageStat(v.getDouble("s"), v.getLong("t"))
        }
        return out
    }

    fun saveUsage(usage: Map<String, UsageStat>) {
        val o = JSONObject()
        usage.forEach { (k, v) -> o.put(k, JSONObject().put("s", v.score).put("t", v.lastUsed)) }
        prefs.edit().putString("usage", o.toString()).apply()
    }

    // ---- Custom icons (key -> absolute file path) ----
    fun loadCustomIcons(): Map<String, String> {
        val o = parseObject(prefs.getString("custom_icons", null)) ?: return emptyMap()
        val out = HashMap<String, String>()
        o.keys().forEach { k -> out[k] = o.getString(k) }
        return out
    }

    fun saveCustomIcons(map: Map<String, String>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString("custom_icons", o.toString()).apply()
    }

    // ---- Settings ----
    fun loadSettings(): LauncherSettings {
        val shape = try {
            IconShape.valueOf(prefs.getString("icon_shape", IconShape.SQUIRCLE.name)!!)
        } catch (e: Exception) {
            IconShape.SQUIRCLE
        }
        return LauncherSettings(
            iconShape = shape,
            iconSizeDp = prefs.getInt("icon_size", 64),
            showLabels = prefs.getBoolean("show_labels", true),
        )
    }

    fun saveSettings(s: LauncherSettings) {
        prefs.edit()
            .putString("icon_shape", s.iconShape.name)
            .putInt("icon_size", s.iconSizeDp)
            .putBoolean("show_labels", s.showLabels)
            .apply()
    }

    // ---- Home screen app shortcuts ----
    fun loadShortcuts(): List<HomeShortcut> {
        val arr = parseArray(prefs.getString("shortcuts", null)) ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            HomeShortcut(
                key = o.getString("key"),
                x = o.getDouble("x").toFloat(),
                y = o.getDouble("y").toFloat(),
            )
        }
    }

    fun saveShortcuts(list: List<HomeShortcut>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(
                JSONObject()
                    .put("key", s.key)
                    .put("x", s.x.toDouble())
                    .put("y", s.y.toDouble())
            )
        }
        prefs.edit().putString("shortcuts", arr.toString()).apply()
    }

    // ---- Home screen widgets ----
    fun loadWidgets(): List<HomeWidget> {
        val arr = parseArray(prefs.getString("widgets", null)) ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            HomeWidget(
                id = o.getInt("id"),
                x = o.getDouble("x").toFloat(),
                y = o.getDouble("y").toFloat(),
                w = o.getDouble("w").toFloat(),
                h = o.getDouble("h").toFloat(),
            )
        }
    }

    fun saveWidgets(widgets: List<HomeWidget>) {
        val arr = JSONArray()
        widgets.forEach { w ->
            arr.put(
                JSONObject()
                    .put("id", w.id)
                    .put("x", w.x.toDouble())
                    .put("y", w.y.toDouble())
                    .put("w", w.w.toDouble())
                    .put("h", w.h.toDouble())
            )
        }
        prefs.edit().putString("widgets", arr.toString()).apply()
    }
}

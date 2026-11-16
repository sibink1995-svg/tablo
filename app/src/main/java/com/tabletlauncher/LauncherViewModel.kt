package com.tabletlauncher

import android.app.Application
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tabletlauncher.data.AppEntry
import com.tabletlauncher.data.Folder
import com.tabletlauncher.data.HomeShortcut
import com.tabletlauncher.data.HomeWidget
import com.tabletlauncher.data.IconShape
import com.tabletlauncher.data.LauncherSettings
import com.tabletlauncher.data.LauncherStore
import com.tabletlauncher.data.UsageHelper
import com.tabletlauncher.data.UsageStat
import com.tabletlauncher.data.categoryTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.pow
import kotlin.math.roundToInt

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx: Context = application.applicationContext
    private val pm: PackageManager = ctx.packageManager
    private val store = LauncherStore(ctx)

    // ---- Widgets host ----
    val widgetManager: AppWidgetManager = AppWidgetManager.getInstance(ctx)
    val widgetHost = AppWidgetHost(ctx, HOST_ID)

    // ---- UI state ----
    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var appMap by mutableStateOf<Map<String, AppEntry>>(emptyMap())
        private set
    val icons = mutableStateMapOf<String, ImageBitmap>()

    var pins by mutableStateOf(store.loadPins())
        private set
    var folders by mutableStateOf(store.loadFolders())
        private set
    var settings by mutableStateOf(store.loadSettings())
        private set
    var widgets by mutableStateOf(store.loadWidgets())
        private set
    var shortcuts by mutableStateOf(store.loadShortcuts())
        private set
    var suggestions by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var usageAccess by mutableStateOf(false)
        private set
    var customIconKeys by mutableStateOf<Set<String>>(emptySet())
        private set

    var drawerOpen by mutableStateOf(false)
    var editMode by mutableStateOf(false)

    private val usage: MutableMap<String, UsageStat> = store.loadUsage().toMutableMap()
    private val customIcons: MutableMap<String, String> = store.loadCustomIcons().toMutableMap()

    init {
        customIconKeys = customIcons.keys.toSet()
        reloadApps()
    }

    // =====================================================================
    // Apps
    // =====================================================================

    fun reloadApps() {
        viewModelScope.launch(Dispatchers.Default) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(query, 0)
                .filter { it.activityInfo.packageName != ctx.packageName }
                .map { ri ->
                    val ai = ri.activityInfo
                    AppEntry(
                        key = "${ai.packageName}/${ai.name}",
                        packageName = ai.packageName,
                        className = ai.name,
                        label = ri.loadLabel(pm).toString(),
                        category = ai.applicationInfo.category,
                    )
                }
                .sortedBy { it.label.lowercase() }

            val loaded = HashMap<String, ImageBitmap>()
            list.forEach { app -> loadBitmap(app)?.let { loaded[app.key] = it } }

            withContext(Dispatchers.Main) {
                apps = list
                appMap = list.associateBy { it.key }
                icons.putAll(loaded)
                refreshSuggestions()
            }
        }
    }

    private fun loadBitmap(app: AppEntry): ImageBitmap? = runCatching {
        val custom = customIcons[app.key]
        if (custom != null && File(custom).exists()) {
            BitmapFactory.decodeFile(custom)?.asImageBitmap()
        } else {
            pm.getActivityIcon(app.component).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
        }
    }.getOrNull()

    fun launch(app: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
            return
        }
        recordLaunch(app.key)
        drawerOpen = false
        editMode = false
    }

    // =====================================================================
    // Suggestions (frequently / recently used)
    // =====================================================================

    private fun decay(dtMs: Long): Double = 0.5.pow(dtMs.coerceAtLeast(0) / HALF_LIFE_MS)

    private fun recordLaunch(key: String) {
        val now = System.currentTimeMillis()
        val old = usage[key]
        val score = (old?.score ?: 0.0) * decay(now - (old?.lastUsed ?: now)) + 1.0
        usage[key] = UsageStat(score, now)
        store.saveUsage(usage)
        refreshSuggestions()
    }

    fun refreshSuggestions() {
        viewModelScope.launch(Dispatchers.Default) {
            val access = UsageHelper.hasAccess(ctx)
            val minutes = if (access) {
                runCatching { UsageHelper.minutesLastWeek(ctx) }.getOrDefault(emptyMap())
            } else {
                emptyMap()
            }
            val now = System.currentTimeMillis()
            val top = apps
                .map { app ->
                    val u = usage[app.key]
                    val launchScore = u?.let { it.score * decay(now - it.lastUsed) } ?: 0.0
                    val timeScore = (minutes[app.packageName] ?: 0.0) / 10.0 // 10 min ~ 1 launch
                    app to (launchScore + timeScore)
                }
                .filter { it.second > 0.05 }
                .sortedByDescending { it.second }
                .map { it.first }
                .distinctBy { it.packageName }
                .take(SUGGESTION_COUNT)
            withContext(Dispatchers.Main) {
                usageAccess = access
                suggestions = top
            }
        }
    }

    fun clearUsageHistory() {
        usage.clear()
        store.saveUsage(usage)
        refreshSuggestions()
    }

    // =====================================================================
    // Pins
    // =====================================================================

    fun isPinned(key: String) = key in pins

    fun togglePin(key: String) {
        pins = if (key in pins) pins - key else pins + key
        store.savePins(pins)
    }

    // =====================================================================
    // Home screen app shortcuts
    // =====================================================================

    private var homeW = 1000f // dp, reported by HomeScreen
    private var homeH = 700f

    fun setHomeSize(wDp: Float, hDp: Float) {
        homeW = wDp
        homeH = hDp
    }

    private val cellW: Float get() = (settings.iconSizeDp + 32).toFloat()
    private val cellH: Float get() = (settings.iconSizeDp + if (settings.showLabels) 44 else 24).toFloat()
    private val cols: Int get() = (homeW / cellW).toInt().coerceAtLeast(1)
    private val rows: Int
        get() = ((homeH - HOME_TOP_MARGIN - HOME_BOTTOM_RESERVED) / cellH).toInt().coerceAtLeast(1)

    fun isOnHome(key: String) = shortcuts.any { it.key == key }

    /** Puts the app in the first free grid cell (left to right, top to bottom). */
    fun addToHome(key: String) {
        if (isOnHome(key)) return
        val cw = cellW
        val ch = cellH
        val taken = shortcuts.map {
            Pair((it.x / cw).roundToInt(), ((it.y - HOME_TOP_MARGIN) / ch).roundToInt())
        }.toSet()
        var spot: Pair<Int, Int>? = null
        search@ for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (Pair(c, r) !in taken) {
                    spot = Pair(c, r)
                    break@search
                }
            }
        }
        // Screen full: stack on the last cell rather than losing the request
        val (c, r) = spot ?: Pair(cols - 1, rows - 1)
        shortcuts = shortcuts + HomeShortcut(key, c * cw, HOME_TOP_MARGIN + r * ch)
        store.saveShortcuts(shortcuts)
    }

    fun removeFromHome(key: String) {
        shortcuts = shortcuts.filterNot { it.key == key }
        store.saveShortcuts(shortcuts)
    }

    fun moveShortcutBy(key: String, dx: Float, dy: Float) {
        shortcuts = shortcuts.map {
            if (it.key == key) it.copy(x = it.x + dx, y = it.y + dy) else it
        }
    }

    /** Called when a drag ends: snap to the nearest grid cell inside the screen, then save. */
    fun snapShortcut(key: String) {
        val cw = cellW
        val ch = cellH
        shortcuts = shortcuts.map {
            if (it.key != key) {
                it
            } else {
                val c = (it.x / cw).roundToInt().coerceIn(0, cols - 1)
                val r = ((it.y - HOME_TOP_MARGIN) / ch).roundToInt().coerceIn(0, rows - 1)
                it.copy(x = c * cw, y = HOME_TOP_MARGIN + r * ch)
            }
        }
        store.saveShortcuts(shortcuts)
    }

    // =====================================================================
    // Folders
    // =====================================================================

    fun folderOf(key: String): Folder? = folders.firstOrNull { key in it.appKeys }

    fun createFolder(name: String, keys: List<String>) {
        val id = UUID.randomUUID().toString()
        folders = folders + Folder(id, name.trim().ifEmpty { "Folder" }, emptyList())
        updateFolder(id, name, keys)
    }

    /** An app can live in only one folder, so [keys] are removed from all other folders. */
    fun updateFolder(id: String, name: String, keys: List<String>) {
        val keySet = keys.toSet()
        folders = folders.map { f ->
            if (f.id == id) {
                f.copy(name = name.trim().ifEmpty { f.name }, appKeys = keys.distinct())
            } else {
                f.copy(appKeys = f.appKeys.filterNot { it in keySet })
            }
        }
        store.saveFolders(folders)
    }

    fun deleteFolder(id: String) {
        folders = folders.filterNot { it.id == id }
        store.saveFolders(folders)
    }

    fun addToFolder(id: String, key: String) {
        val f = folders.firstOrNull { it.id == id } ?: return
        updateFolder(id, f.name, f.appKeys + key)
    }

    fun removeFromFolder(key: String) {
        folders = folders.map { it.copy(appKeys = it.appKeys - key) }
        store.saveFolders(folders)
    }

    /**
     * Groups apps that aren't in a folder yet by the category the app declares
     * (Games, Social, Music, ...). Returns how many apps were grouped.
     */
    fun autoOrganize(): Int {
        val inFolder = folders.flatMap { it.appKeys }.toSet()
        val groups = apps
            .filter { it.key !in inFolder && it.category != ApplicationInfo.CATEGORY_UNDEFINED }
            .groupBy { categoryTitle(it.category) }

        var moved = 0
        var result = folders
        for ((title, list) in groups) {
            val existing = result.firstOrNull { it.name == title }
            if (existing == null && list.size < 2) continue
            moved += list.size
            val keys = list.map { it.key }
            result = if (existing != null) {
                result.map { if (it.id == existing.id) it.copy(appKeys = it.appKeys + keys) else it }
            } else {
                result + Folder(UUID.randomUUID().toString(), title, keys)
            }
        }
        folders = result
        store.saveFolders(folders)
        return moved
    }

    // =====================================================================
    // Icon customisation
    // =====================================================================

    fun setIconShape(shape: IconShape) {
        settings = settings.copy(iconShape = shape)
        commitSettings()
    }

    /** Updates live while the slider moves; call [commitSettings] when it is released. */
    fun setIconSize(dp: Int) {
        settings = settings.copy(iconSizeDp = dp)
    }

    fun setShowLabels(show: Boolean) {
        settings = settings.copy(showLabels = show)
        commitSettings()
    }

    fun commitSettings() = store.saveSettings(settings)

    fun hasCustomIcon(key: String) = key in customIconKeys

    fun setCustomIcon(key: String, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1
                while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val src = ctx.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                } ?: return@launch

                val side = minOf(src.width, src.height)
                val square = Bitmap.createBitmap(
                    src, (src.width - side) / 2, (src.height - side) / 2, side, side,
                )
                val scaled = Bitmap.createScaledBitmap(square, ICON_PX, ICON_PX, true)

                val dir = File(ctx.filesDir, "icons").apply { mkdirs() }
                val file = File(dir, "${key.hashCode().toUInt()}.png")
                FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }

                withContext(Dispatchers.Main) {
                    customIcons[key] = file.absolutePath
                    store.saveCustomIcons(customIcons)
                    customIconKeys = customIcons.keys.toSet()
                    icons[key] = scaled.asImageBitmap()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Couldn't use that image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun resetCustomIcon(key: String) {
        customIcons.remove(key)?.let { File(it).delete() }
        store.saveCustomIcons(customIcons)
        customIconKeys = customIcons.keys.toSet()
        val app = appMap[key] ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val bmp = loadBitmap(app)
            withContext(Dispatchers.Main) { if (bmp != null) icons[key] = bmp }
        }
    }

    // =====================================================================
    // Home screen widgets
    // =====================================================================

    fun installedWidgets(): List<AppWidgetProviderInfo> =
        widgetManager.installedProviders
            .filter { (it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) != 0 }
            .sortedBy { it.loadLabel(pm).lowercase() }

    fun addWidget(id: Int, wDp: Float, hDp: Float) {
        val step = (widgets.size % 5) * 32f
        widgets = widgets + HomeWidget(id, 32f + step, 96f + step, wDp, hDp)
        store.saveWidgets(widgets)
    }

    fun moveWidgetBy(id: Int, dx: Float, dy: Float) {
        widgets = widgets.map {
            if (it.id == id) it.copy(x = (it.x + dx).coerceAtLeast(0f), y = (it.y + dy).coerceAtLeast(0f)) else it
        }
    }

    fun resizeWidgetBy(id: Int, dw: Float, dh: Float) {
        widgets = widgets.map {
            if (it.id == id) it.copy(w = (it.w + dw).coerceAtLeast(MIN_WIDGET_DP), h = (it.h + dh).coerceAtLeast(MIN_WIDGET_DP)) else it
        }
    }

    fun commitWidgets() = store.saveWidgets(widgets)

    fun removeWidget(id: Int) {
        runCatching { widgetHost.deleteAppWidgetId(id) }
        widgets = widgets.filterNot { it.id == id }
        store.saveWidgets(widgets)
    }

    fun discardWidgetId(id: Int) {
        runCatching { widgetHost.deleteAppWidgetId(id) }
    }

    companion object {
        const val HOST_ID = 1024
        private const val ICON_PX = 192
        private const val SUGGESTION_COUNT = 8
        private const val HALF_LIFE_MS = 3.0 * 24 * 60 * 60 * 1000
        private const val MIN_WIDGET_DP = 96f
        private const val HOME_TOP_MARGIN = 64f      // keeps clear of the edit-mode bar
        private const val HOME_BOTTOM_RESERVED = 130f // keeps clear of the dock
    }
}

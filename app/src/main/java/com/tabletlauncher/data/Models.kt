package com.tabletlauncher.data

import android.content.ComponentName
import android.content.pm.ApplicationInfo

data class AppEntry(
    val key: String, // "package/activityClass"
    val packageName: String,
    val className: String,
    val label: String,
    val category: Int,
) {
    val component: ComponentName get() = ComponentName(packageName, className)
}

fun categoryTitle(category: Int): String = when (category) {
    ApplicationInfo.CATEGORY_GAME -> "Games"
    ApplicationInfo.CATEGORY_AUDIO -> "Music"
    ApplicationInfo.CATEGORY_VIDEO -> "Video"
    ApplicationInfo.CATEGORY_IMAGE -> "Photos"
    ApplicationInfo.CATEGORY_SOCIAL -> "Social"
    ApplicationInfo.CATEGORY_NEWS -> "News"
    ApplicationInfo.CATEGORY_MAPS -> "Maps"
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
    else -> "Other"
}

data class Folder(
    val id: String,
    val name: String,
    val appKeys: List<String>,
)

enum class IconShape(val title: String) {
    ORIGINAL("Original"),
    CIRCLE("Circle"),
    SQUIRCLE("Rounded"),
    SQUARE("Square"),
}

data class LauncherSettings(
    val iconShape: IconShape = IconShape.SQUIRCLE,
    val iconSizeDp: Int = 64,
    val showLabels: Boolean = true,
)

/** Launch score decays with a 3 day half-life; see LauncherViewModel. */
data class UsageStat(
    val score: Double,
    val lastUsed: Long,
)

/** An app icon placed on the home screen. Position is stored in dp. */
data class HomeShortcut(
    val key: String,
    val x: Float,
    val y: Float,
)

/** Position and size are stored in dp. [id] is the AppWidgetHost widget id. */
data class HomeWidget(
    val id: Int,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
)

package com.tabletlauncher.ui

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tabletlauncher.LauncherViewModel
import com.tabletlauncher.data.AppEntry
import com.tabletlauncher.data.HomeWidget

@Composable
fun HomeScreen(
    vm: LauncherViewModel,
    onOpenDrawer: () -> Unit,
    onAppLongClick: (AppEntry) -> Unit,
    onAddWidget: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val density = LocalDensity.current.density
    val pinned = vm.pins.mapNotNull { vm.appMap[it] }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { vm.setHomeSize(it.width / density, it.height / density) }
            // Long-press empty space -> edit mode (add / move / resize widgets)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { vm.editMode = true })
            }
            // Swipe up -> app drawer
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -120f) onOpenDrawer() },
                    onDragCancel = { },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        total += dy
                    },
                )
            }
    ) {
        // ---------------- Widget canvas ----------------
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            vm.widgets.forEach { w ->
                key(w.id) {
                    val info = remember(w.id) { vm.widgetManager.getAppWidgetInfo(w.id) }
                    if (info == null) {
                        // Provider was uninstalled -> forget the widget
                        LaunchedEffect(w.id) { vm.removeWidget(w.id) }
                    } else {
                        Box(
                            Modifier
                                .offset(w.x.dp, w.y.dp)
                                .size(w.w.dp, w.h.dp)
                                .clip(RoundedCornerShape(20.dp))
                        ) {
                            WidgetView(vm, w, info)
                            if (vm.editMode) {
                                // Drag anywhere on the widget to move it
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .background(Color(0x44000000))
                                        .border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                                        .pointerInput(w.id) {
                                            detectDragGestures(
                                                onDragEnd = { vm.commitWidgets() },
                                                onDragCancel = { vm.commitWidgets() },
                                            ) { change, drag ->
                                                change.consume()
                                                vm.moveWidgetBy(w.id, drag.x / density, drag.y / density)
                                            }
                                        }
                                )
                                IconButton(
                                    onClick = { vm.removeWidget(w.id) },
                                    modifier = Modifier.align(Alignment.TopEnd),
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove widget", tint = Color.White)
                                }
                                // Resize handle (bottom-right)
                                Box(
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(44.dp)
                                        .pointerInput(w.id) {
                                            detectDragGestures(
                                                onDragEnd = { vm.commitWidgets() },
                                                onDragCancel = { vm.commitWidgets() },
                                            ) { change, drag ->
                                                change.consume()
                                                vm.resizeWidgetBy(w.id, drag.x / density, drag.y / density)
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("↘", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---------------- App shortcuts on the home screen ----------------
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            vm.shortcuts.forEach { sc ->
                val app = vm.appMap[sc.key] ?: return@forEach
                key(sc.key) {
                    Box(Modifier.offset(sc.x.dp, sc.y.dp)) {
                        AppIcon(
                            app = app,
                            vm = vm,
                            onClick = { vm.launch(app) },
                            onLongClick = { onAppLongClick(app) },
                        )
                        if (vm.editMode) {
                            // Drag to move; snaps to the grid on release
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .background(Color(0x33000000), RoundedCornerShape(16.dp))
                                    .pointerInput(sc.key) {
                                        detectDragGestures(
                                            onDragEnd = { vm.snapShortcut(sc.key) },
                                            onDragCancel = { vm.snapShortcut(sc.key) },
                                        ) { change, drag ->
                                            change.consume()
                                            vm.moveShortcutBy(sc.key, drag.x / density, drag.y / density)
                                        }
                                    }
                            )
                            IconButton(
                                onClick = { vm.removeFromHome(sc.key) },
                                modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove from home screen",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(Color(0xAA000000), CircleShape),
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------------- Edit mode bar ----------------
        if (vm.editMode) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xCC1B1F27))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onAddWidget) { Text("+ Widget") }
                TextButton(onClick = onOpenSettings) { Text("Icons & settings") }
                TextButton(onClick = { vm.editMode = false }) { Text("Done") }
            }
        } else if (vm.widgets.isEmpty()) {
            Text(
                "Swipe up for apps  •  Long-press to add widgets",
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // ---------------- Dock (pinned apps) ----------------
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(12.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0x66000000))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pinned.take(7).forEach { app ->
                AppIcon(
                    app = app,
                    vm = vm,
                    showLabel = false,
                    onClick = { vm.launch(app) },
                    onLongClick = { onAppLongClick(app) },
                )
            }
            Box(Modifier.padding(8.dp)) {
                Box(
                    Modifier
                        .size(vm.settings.iconSizeDp.dp)
                        .clip(CircleShape)
                        .background(Color(0x55FFFFFF))
                        .clickable { onOpenDrawer() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Apps, contentDescription = "All apps", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun WidgetView(vm: LauncherViewModel, widget: HomeWidget, info: AppWidgetProviderInfo) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { c -> vm.widgetHost.createView(c, widget.id, info) },
        update = { v ->
            v.updateAppWidgetSize(
                null,
                widget.w.toInt(), widget.h.toInt(),
                widget.w.toInt(), widget.h.toInt(),
            )
        },
    )
}

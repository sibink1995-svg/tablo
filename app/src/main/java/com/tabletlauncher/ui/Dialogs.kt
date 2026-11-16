@file:OptIn(ExperimentalMaterial3Api::class)

package com.tabletlauncher.ui

import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tabletlauncher.LauncherViewModel
import com.tabletlauncher.data.AppEntry
import com.tabletlauncher.data.Folder
import com.tabletlauncher.data.IconShape
import kotlin.math.roundToInt

/** Wide, rounded dialog shell that works well on tablets. */
@Composable
fun LauncherDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(0.92f)
                .heightIn(max = 680.dp),
        ) {
            Column(Modifier.padding(20.dp), content = content)
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

// =========================================================================
// Long-press menu for an app
// =========================================================================

@Composable
fun AppActionsDialog(
    vm: LauncherViewModel,
    app: AppEntry,
    onDismiss: () -> Unit,
    onAddToFolder: () -> Unit,
    onCustomizeIcon: () -> Unit,
) {
    val ctx = LocalContext.current
    LauncherDialog(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconImage(vm.icons[app.key], vm.settings, app.label, 48.dp)
            Spacer(Modifier.width(14.dp))
            Text(
                app.label,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(12.dp))

        val pinned = vm.isPinned(app.key)
        ActionRow(Icons.Filled.PushPin, if (pinned) "Unpin" else "Pin (top of drawer & dock)") {
            vm.togglePin(app.key)
            onDismiss()
        }
        if (vm.isOnHome(app.key)) {
            ActionRow(Icons.Filled.Home, "Remove from home screen") {
                vm.removeFromHome(app.key)
                onDismiss()
            }
        } else {
            ActionRow(Icons.Filled.Home, "Add to home screen") {
                vm.addToHome(app.key)
                vm.drawerOpen = false // go to the home screen so the new icon is visible
                Toast.makeText(ctx, "${app.label} added to home screen", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        }
        ActionRow(Icons.Filled.Folder, "Add to folder…", onAddToFolder)
        if (vm.folderOf(app.key) != null) {
            ActionRow(Icons.Filled.Close, "Remove from folder") {
                vm.removeFromFolder(app.key)
                onDismiss()
            }
        }
        ActionRow(Icons.Filled.Palette, "Customize icon…", onCustomizeIcon)
        ActionRow(Icons.Filled.Info, "App info") {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
            )
            onDismiss()
        }
        ActionRow(Icons.Filled.Delete, "Uninstall") {
            ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
            onDismiss()
        }
    }
}

// =========================================================================
// Folders
// =========================================================================

@Composable
fun FolderPickerDialog(
    vm: LauncherViewModel,
    app: AppEntry,
    onDismiss: () -> Unit,
    onNewFolder: () -> Unit,
) {
    LauncherDialog(onDismiss) {
        Text("Add “${app.label}” to folder", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(vm.folders, key = { it.id }) { f ->
                ActionRow(Icons.Filled.Folder, f.name) {
                    vm.addToFolder(f.id, app.key)
                    onDismiss()
                }
            }
        }
        ActionRow(Icons.Filled.CreateNewFolder, "New folder…", onNewFolder)
    }
}

@Composable
fun FolderDialog(
    vm: LauncherViewModel,
    folder: Folder,
    onDismiss: () -> Unit,
    onAppLongClick: (AppEntry) -> Unit,
    onEdit: () -> Unit,
) {
    val apps = folder.appKeys.mapNotNull { vm.appMap[it] }
    LauncherDialog(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                folder.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit folder") }
            IconButton(onClick = {
                vm.deleteFolder(folder.id)
                onDismiss()
            }) { Icon(Icons.Filled.Delete, contentDescription = "Delete folder (apps stay)") }
        }
        Spacer(Modifier.height(8.dp))
        if (apps.isEmpty()) {
            Text("Empty folder. Tap the pencil to add apps.", modifier = Modifier.alpha(0.7f))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive((vm.settings.iconSizeDp + 32).dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                items(apps, key = { it.key }) { app ->
                    AppIcon(
                        app = app,
                        vm = vm,
                        onClick = {
                            vm.launch(app)
                            onDismiss()
                        },
                        onLongClick = { onAppLongClick(app) },
                    )
                }
            }
        }
    }
}

/** Create ([folder] == null) or edit a folder: name + checklist of apps. */
@Composable
fun FolderEditorDialog(
    vm: LauncherViewModel,
    folder: Folder?,
    preselect: String?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(folder?.name ?: "") }
    var selected by remember { mutableStateOf(folder?.appKeys?.toSet() ?: setOfNotNull(preselect)) }

    LauncherDialog(onDismiss) {
        Text(
            if (folder == null) "New folder" else "Edit folder",
            style = MaterialTheme.typography.titleLarge,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Folder name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Text(
            "Apps",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(vm.apps, key = { it.key }) { app ->
                val checked = app.key in selected
                val other = vm.folderOf(app.key)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { selected = if (checked) selected - app.key else selected + app.key }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    IconImage(vm.icons[app.key], vm.settings, app.label, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(app.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (other != null && other.id != folder?.id) {
                        Text(other.name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.alpha(0.6f))
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Button(onClick = {
                if (folder == null) vm.createFolder(name, selected.toList())
                else vm.updateFolder(folder.id, name, selected.toList())
                onDismiss()
            }) { Text("Save") }
        }
    }
}

// =========================================================================
// Icon customisation
// =========================================================================

@Composable
fun IconCustomizeDialog(
    vm: LauncherViewModel,
    app: AppEntry,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    LauncherDialog(onDismiss) {
        Text("Customize icon", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconImage(vm.icons[app.key], vm.settings, app.label, 72.dp)
            Spacer(Modifier.width(16.dp))
            Text(app.label, style = MaterialTheme.typography.titleMedium)
        }
        Button(
            onClick = {
                onPick(app.key)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Image, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Choose image from gallery")
        }
        if (vm.hasCustomIcon(app.key)) {
            OutlinedButton(
                onClick = {
                    vm.resetCustomIcon(app.key)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Reset to original icon") }
        }
        Text(
            "Shape and size for all icons: Settings.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 12.dp).alpha(0.7f),
        )
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
    }
}

// =========================================================================
// Settings
// =========================================================================

@Composable
fun SettingsDialog(
    vm: LauncherViewModel,
    onDismiss: () -> Unit,
    onOpenUsageSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val s = vm.settings
    LauncherDialog(onDismiss) {
        Text("Launcher settings", style = MaterialTheme.typography.titleLarge)
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp),
        ) {
            Text("Icon shape", style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconShape.values().forEach { shape ->
                    FilterChip(
                        selected = s.iconShape == shape,
                        onClick = { vm.setIconShape(shape) },
                        label = { Text(shape.title) },
                    )
                }
            }
            // Live preview
            Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                vm.apps.take(5).forEach { app -> IconImage(vm.icons[app.key], s, app.label) }
            }

            Text(
                "Icon size: ${s.iconSizeDp} dp",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
            Slider(
                value = s.iconSizeDp.toFloat(),
                onValueChange = { vm.setIconSize(it.roundToInt()) },
                onValueChangeFinished = { vm.commitSettings() },
                valueRange = 48f..96f,
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Show app names", modifier = Modifier.weight(1f))
                Switch(checked = s.showLabels, onCheckedChange = { vm.setShowLabels(it) })
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Text("Suggestions", style = MaterialTheme.typography.labelLarge)
            Text(
                "Suggested apps are based on what you open from this launcher. " +
                    "Allow usage access for better results using real screen time.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp).alpha(0.7f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (vm.usageAccess) {
                    Text("Usage access: on", modifier = Modifier.weight(1f))
                } else {
                    Text("Usage access: off", modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = onOpenUsageSettings) { Text("Allow") }
                }
            }
            TextButton(onClick = { vm.clearUsageHistory() }) { Text("Clear suggestion history") }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            OutlinedButton(
                onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Set as default launcher") }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
    }
}

// =========================================================================
// Widget picker
// =========================================================================

@Composable
fun WidgetPickerDialog(
    vm: LauncherViewModel,
    onDismiss: () -> Unit,
    onPick: (AppWidgetProviderInfo) -> Unit,
) {
    val ctx = LocalContext.current
    val providers = remember { vm.installedWidgets() }

    LauncherDialog(onDismiss) {
        Text("Add widget", style = MaterialTheme.typography.titleLarge)
        if (providers.isEmpty()) {
            Text("No widgets found on this device.", modifier = Modifier.padding(top = 12.dp))
        }
        LazyColumn(Modifier.weight(1f, fill = false).padding(top = 12.dp)) {
            items(providers, key = { it.provider.flattenToString() }) { info ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            onPick(info)
                            onDismiss()
                        }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WidgetPreview(info)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(info.loadLabel(ctx.packageManager).toString(), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            info.provider.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.alpha(0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetPreview(info: AppWidgetProviderInfo) {
    val ctx = LocalContext.current
    val bitmap = remember(info) {
        runCatching {
            val drawable = info.loadPreviewImage(ctx, 0) ?: info.loadIcon(ctx, 0)
            drawable.toBitmapScaled(320).asImageBitmap()
        }.getOrNull()
    }
    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(96.dp))
        }
    }
}

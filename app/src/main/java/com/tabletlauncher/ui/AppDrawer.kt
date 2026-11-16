@file:OptIn(ExperimentalMaterial3Api::class)

package com.tabletlauncher.ui

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tabletlauncher.LauncherViewModel
import com.tabletlauncher.data.AppEntry
import com.tabletlauncher.data.Folder

@Composable
fun AppDrawer(
    vm: LauncherViewModel,
    onClose: () -> Unit,
    onAppLongClick: (AppEntry) -> Unit,
    onOpenFolder: (Folder) -> Unit,
    onNewFolder: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    val s = vm.settings

    val inFolders = remember(vm.folders) { vm.folders.flatMap { it.appKeys }.toSet() }
    val searching = query.isNotBlank()
    val shown = if (searching) {
        vm.apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    } else {
        vm.apps.filter { it.key !in inFolders }
    }
    val pinned = vm.pins.mapNotNull { vm.appMap[it] }

    Surface(color = Color(0xF2101418), modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp)
        ) {
            // ---- Search bar ----
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        shown.firstOrNull()?.let { vm.launch(it) }
                    }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive((s.iconSizeDp + 40).dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (!searching) {
                    // ---- Suggested: most used recently ----
                    if (vm.suggestions.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Suggested") }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            AppRow(vm.suggestions, vm, onAppLongClick)
                        }
                    }
                    // ---- Pinned ----
                    if (pinned.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Pinned") }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            AppRow(pinned, vm, onAppLongClick)
                        }
                    }
                    // ---- Folders ----
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader("Folders") {
                            TextButton(onClick = {
                                val n = vm.autoOrganize()
                                Toast.makeText(
                                    ctx,
                                    if (n > 0) "Grouped $n apps into folders"
                                    else "Nothing to group (apps don't declare a category)",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }) { Text("Auto-organise") }
                            TextButton(onClick = onNewFolder) { Text("New folder") }
                        }
                    }
                    items(vm.folders, key = { it.id }) { folder ->
                        FolderTile(folder, vm, onClick = { onOpenFolder(folder) })
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("All apps") }
                }

                items(shown, key = { it.key }) { app ->
                    AppIcon(
                        app = app,
                        vm = vm,
                        onClick = { vm.launch(app) },
                        onLongClick = { onAppLongClick(app) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(apps: List<AppEntry>, vm: LauncherViewModel, onAppLongClick: (AppEntry) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        apps.forEach { app ->
            AppIcon(
                app = app,
                vm = vm,
                onClick = { vm.launch(app) },
                onLongClick = { onAppLongClick(app) },
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp, start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

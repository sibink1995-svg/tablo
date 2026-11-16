package com.tabletlauncher.ui

import android.appwidget.AppWidgetProviderInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tabletlauncher.LauncherViewModel
import com.tabletlauncher.data.AppEntry

private data class FolderEditorState(val folderId: String?, val preselect: String? = null)

@Composable
fun LauncherRoot(
    vm: LauncherViewModel,
    onAddWidget: (AppWidgetProviderInfo) -> Unit,
    onPickIcon: (String) -> Unit,
    onOpenUsageSettings: () -> Unit,
) {
    var actionApp by remember { mutableStateOf<AppEntry?>(null) }
    var folderPickerApp by remember { mutableStateOf<AppEntry?>(null) }
    var iconApp by remember { mutableStateOf<AppEntry?>(null) }
    var openFolderId by remember { mutableStateOf<String?>(null) }
    var folderEditor by remember { mutableStateOf<FolderEditorState?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showWidgetPicker by remember { mutableStateOf(false) }

    BackHandler(enabled = vm.drawerOpen || vm.editMode) {
        if (vm.drawerOpen) vm.drawerOpen = false else vm.editMode = false
    }

    Box(Modifier.fillMaxSize()) {
        HomeScreen(
            vm = vm,
            onOpenDrawer = { vm.drawerOpen = true },
            onAppLongClick = { actionApp = it },
            onAddWidget = { showWidgetPicker = true },
            onOpenSettings = { showSettings = true },
        )

        AnimatedVisibility(
            visible = vm.drawerOpen,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            AppDrawer(
                vm = vm,
                onClose = { vm.drawerOpen = false },
                onAppLongClick = { actionApp = it },
                onOpenFolder = { openFolderId = it.id },
                onNewFolder = { folderEditor = FolderEditorState(null) },
                onOpenSettings = { showSettings = true },
            )
        }
    }

    // ---------------- Dialogs ----------------

    actionApp?.let { app ->
        AppActionsDialog(
            vm = vm,
            app = app,
            onDismiss = { actionApp = null },
            onAddToFolder = {
                folderPickerApp = app
                actionApp = null
            },
            onCustomizeIcon = {
                iconApp = app
                actionApp = null
            },
        )
    }

    folderPickerApp?.let { app ->
        FolderPickerDialog(
            vm = vm,
            app = app,
            onDismiss = { folderPickerApp = null },
            onNewFolder = {
                folderEditor = FolderEditorState(null, preselect = app.key)
                folderPickerApp = null
            },
        )
    }

    iconApp?.let { app ->
        IconCustomizeDialog(vm, app, onDismiss = { iconApp = null }, onPick = onPickIcon)
    }

    openFolderId?.let { id ->
        vm.folders.firstOrNull { it.id == id }?.let { folder ->
            FolderDialog(
                vm = vm,
                folder = folder,
                onDismiss = { openFolderId = null },
                onAppLongClick = { actionApp = it },
                onEdit = {
                    folderEditor = FolderEditorState(folder.id)
                    openFolderId = null
                },
            )
        }
    }

    folderEditor?.let { state ->
        FolderEditorDialog(
            vm = vm,
            folder = state.folderId?.let { id -> vm.folders.firstOrNull { it.id == id } },
            preselect = state.preselect,
            onDismiss = { folderEditor = null },
        )
    }

    if (showSettings) {
        SettingsDialog(vm, onDismiss = { showSettings = false }, onOpenUsageSettings = onOpenUsageSettings)
    }

    if (showWidgetPicker) {
        WidgetPickerDialog(vm, onDismiss = { showWidgetPicker = false }, onPick = onAddWidget)
    }
}

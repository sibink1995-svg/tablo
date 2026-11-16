package com.tabletlauncher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.tabletlauncher.ui.LauncherRoot
import com.tabletlauncher.ui.LauncherTheme

class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()

    private var pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pickIconKey: String? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            vm.reloadApps()
        }
    }

    private val imagePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val key = pickIconKey
            if (uri != null && key != null) vm.setCustomIcon(key, uri)
            pickIconKey = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LauncherTheme {
                LauncherRoot(
                    vm = vm,
                    onAddWidget = ::startAddWidget,
                    onPickIcon = { key ->
                        pickIconKey = key
                        imagePicker.launch("image/*")
                    },
                    onOpenUsageSettings = {
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.widgetHost.startListening()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        super.onStop()
        vm.widgetHost.stopListening()
        runCatching { unregisterReceiver(packageReceiver) }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshSuggestions()
    }

    // Pressing Home while the launcher is already open: go back to the plain home screen.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) {
            vm.drawerOpen = false
            vm.editMode = false
        }
    }

    // =====================================================================
    // Adding a widget: allocate id -> bind (may ask the user) -> configure (if needed) -> place
    // =====================================================================

    private fun startAddWidget(info: AppWidgetProviderInfo) {
        val id = vm.widgetHost.allocateAppWidgetId()
        pendingWidgetId = id
        val bound = vm.widgetManager.bindAppWidgetIdIfAllowed(id, info.provider)
        if (bound) {
            configureOrFinish(id)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            }
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(intent, REQ_BIND)
            } catch (e: Exception) {
                cancelPendingWidget()
            }
        }
    }

    private fun configureOrFinish(id: Int) {
        val info = vm.widgetManager.getAppWidgetInfo(id)
        if (info?.configure != null) {
            try {
                vm.widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE, null)
            } catch (e: Exception) {
                finishAddWidget(id)
            }
        } else {
            finishAddWidget(id)
        }
    }

    private fun finishAddWidget(id: Int) {
        val info = vm.widgetManager.getAppWidgetInfo(id)
        if (info == null) {
            cancelPendingWidget()
            return
        }
        val density = resources.displayMetrics.density
        // AppWidgetProviderInfo sizes are reported in pixels.
        val w = (info.minWidth / density).coerceIn(120f, 600f)
        val h = (info.minHeight / density).coerceIn(100f, 500f)
        vm.addWidget(id, w, h)
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    }

    private fun cancelPendingWidget() {
        if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            vm.discardWidgetId(pendingWidgetId)
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_BIND ->
                if (resultCode == RESULT_OK) configureOrFinish(pendingWidgetId) else cancelPendingWidget()
            REQ_CONFIGURE ->
                if (resultCode == RESULT_OK) finishAddWidget(pendingWidgetId) else cancelPendingWidget()
        }
    }

    private companion object {
        const val REQ_BIND = 4001
        const val REQ_CONFIGURE = 4002
    }
}

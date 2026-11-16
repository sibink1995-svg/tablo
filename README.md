# Tablet Launcher

A home-screen launcher for Android tablets, written in Kotlin + Jetpack Compose.

## Features

| Feature | How it works |
|---|---|
| **App folders** | Create folders in the app drawer (name + checklist of apps), edit/delete them, or tap **Auto-organise** to group apps by their declared category (Games, Social, Music…). |
| **Apps on the home screen** | Long-press any app (in the drawer, a folder, Suggested…) → **Add to home screen**. Long-press the home screen to enter edit mode, then drag icons to rearrange (they snap to a grid) or tap ✕ to remove. Long-press an icon on the home screen → **Remove from home screen**. |
| **Home screen widgets** | Long-press the home screen → **+ Widget**. Drag a widget to move it, drag the ↘ handle to resize, ✕ to remove. Widgets that need setup open their own configure screen. |
| **Icon customization** | Settings: icon shape (original / circle / rounded / square), icon size, show/hide labels. Per app: long-press → **Customize icon** → pick any image from the gallery, or reset. |
| **Suggested apps** | Top of the app drawer shows your most-used apps (launch count with a 3-day half-life decay). Optionally allow *Usage access* to also factor in real screen time from the last 7 days. |
| **Pin apps** | Long-press an app → **Pin**. Pinned apps appear in a *Pinned* row at the top of the drawer and in the dock on the home screen. |

Other: search, app info / uninstall from the long-press menu, live updates when apps are installed or removed, edge-to-edge layout, works in any orientation.

## Build

**GitHub Actions:** every push builds a debug APK (`.github/workflows/build.yml`). Download it from the run's *Artifacts*.

**Android Studio:** open this folder, let Gradle sync, press Run.

**Command line** (needs JDK 17, Android SDK 34 and Gradle 8.9+):

```bash
gradle assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

Install the APK, press the Home button and choose **Tablet Launcher** → *Always*.

## Project layout

```
app/src/main/java/com/tabletlauncher/
├── MainActivity.kt          # activity, widget bind/configure flow, package-change receiver
├── LauncherViewModel.kt     # all state: apps, pins, folders, suggestions, icons, widgets
├── data/                    # models, SharedPreferences/JSON store, usage-stats helper
└── ui/                      # Compose: home, drawer, dialogs, components
```

## Notes

- `minSdk 26`, `targetSdk 34`.
- Widgets use `AppWidgetHost`; Android asks for permission the first time a widget provider is added.
- No network access and no data leaves the device.

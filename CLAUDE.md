# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

**Requires**: JDK 17+

```bash
# Build debug APK (uses local SDK/Gradle setup)
./build_apk.sh

# Manual gradle build
./gradlew assembleDebug

# Build release
./gradlew assembleRelease
```

The `build_apk.sh` script auto-downloads SDK/Gradle if missing and installs to connected device via ADB.

## ADB Quick Reference

```bash
# Connect wirelessly (Android 11+)
adb pair <ip>:<port>
adb connect <ip>:<port>

# Install
adb install -r -g app/build/outputs/apk/debug/app-debug.apk

# Launch app
adb shell am start -n com.pomoremote/.MainActivity

# View logs
adb logcat -s "PomodoroService"

# Screenshot
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png .
```

## Architecture

**Package**: `com.pomoremote` | **Min SDK**: 26 | **Target SDK**: 34 | **Language**: Kotlin

```
MainActivity.kt          # Hosts NavController + binds to PomodoroService
├── ui/
│   ├── TimerFragment    # Main timer display with circular progress
│   ├── StatsFragment    # Statistics with bar/line graphs
│   ├── SettingsFragment # PreferenceFragmentCompat for settings
│   ├── AboutFragment    # Version info & credits
│   ├── HistoryFragment  # Session history list
│   └── LineGraphView    # Custom Canvas-based graph view
├── service/
│   └── PomodoroService  # Foreground service, owns timer state
├── widget/
│   └── TimerWidgetProvider # Home screen widget provider
├── timer/
│   ├── TimerState       # Data class for timer state
│   ├── OfflineTimer     # Local countdown when server unavailable
│   └── SyncManager      # Coordinates online/offline mode
├── network/
│   └── WebSocketClient  # OkHttp WebSocket to Pomodoro server
└── util/
    ├── UtilPreferenceManager  # SharedPreferences wrapper
    └── SoundManager           # Vibration and notification sounds
```

## Key Patterns

- **Service-Activity Binding**: `MainActivity` binds to `PomodoroService` via `ServiceConnection`. Service broadcasts state changes via `LocalBroadcastManager`.
- **Navigation**: Jetpack Navigation with `BottomNavigationView`. Fragments use `MaterialFadeThrough` transitions.
- **State Flow**: Server → WebSocket → SyncManager → PomodoroService → BroadcastReceiver → UI update
- **Offline Fallback**: `SyncManager` switches to `OfflineTimer` when WebSocket disconnects.

## Dependencies

- **OkHttp** - WebSocket client for server sync
- **Gson** - JSON parsing for timer state
- **Material 3** - UI components and theming
- **Navigation** - Fragment navigation with bottom nav

## Notes

- `network_security_config.xml` allows cleartext traffic for local WebSocket connections
- Timer state is persisted across app restarts via `UtilPreferenceManager`

## Development Workflow

1. **Read & Plan**: Understand the requirements and explore existing code.
2. **Implement**: Write code using idiomatic Kotlin and Android best practices.
3. **Update Version**: Increment `versionCode` and `versionName` in `app/build.gradle.kts` for significant changes.
4. **Verify**:
   - Run `./build_apk.sh` to compile and install.
   - Use `adb logcat -s "PomodoroService"` to debug.
5. **Commit**: Use Conventional Commits (e.g., `feat:`, `fix:`).


# PomoRemote

An Android companion app for syncing with a remote Pomodoro timer server.

## Features

- **Real-time sync** - Connects to a Pomodoro server via WebSocket for synchronized timer state
- **Offline mode** - Full timer functionality when server is unavailable  
- **Foreground service** - Timer continues running when app is in background
- **Notification controls** - Start/pause, skip, and reset directly from notifications
- **Session tracking** - Tracks completed pomodoro sessions

## Requirements

- Android 8.0 (API 26) or higher
- A running [Pomotroid](https://github.com/Splode/pomotroid) or compatible Pomodoro server (for sync features)

## Building

### Prerequisites

- JDK 8+
- Android SDK with:
  - Build Tools 34.0.0
  - Platform SDK 34

### Build APK

```bash
./gradlew assembleDebug
```

The APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`

### Build Release

```bash
./gradlew assembleRelease
```

## Configuration

1. Open the app and tap the settings icon
2. Enter the IP address and port of your Pomodoro server
3. The app will automatically connect when settings are saved

## Architecture

```
com.pomoremote/
├── MainActivity.java          # Main timer UI
├── SettingsActivity.java      # Server configuration
├── service/
│   ├── PomodoroService.java   # Foreground service managing timer
│   ├── NotificationHelper.java # Notification management
│   └── NotificationActionReceiver.java # Handles notification buttons
├── timer/
│   ├── TimerState.java        # Timer state model
│   ├── OfflineTimer.java      # Local timer when offline
│   └── SyncManager.java       # Server sync coordination
├── network/
│   └── WebSocketClient.java   # WebSocket connection handler
└── util/
    └── PreferenceManager.java # Settings persistence
```

## Dependencies

- [OkHttp](https://square.github.io/okhttp/) - WebSocket client
- [Gson](https://github.com/google/gson) - JSON parsing
- AndroidX AppCompat, Material, ConstraintLayout, Preference

## License

MIT

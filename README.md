# Extra Dim Toggle

A tiny rooted-Android utility that flips the system **"Reduce bright colors"**
(Extra Dim) setting with one tap — from the app, a Quick Settings tile, or a
home screen widget.

> **Extra Dim** dims your screen *below* the minimum brightness the slider
> allows — perfect for night reading or saving battery on OLED panels.

## Features

| Surface | What it does |
|---|---|
| **App** | Compose (Material 3) screen showing the current ON/OFF state with a toggle button |
| **Quick Settings tile** | Pull down the shade, tap the tile to toggle |
| **Home screen widget** | One-tap toggle straight from the launcher |

All root work happens off the main thread, so the UI never freezes.

## How it works

The app uses `su` to run the command that updates the secure setting backing
Extra Dim:

```bash
# ON
settings put secure reduce_bright_colors_activated 1
# OFF
settings put secure reduce_bright_colors_activated 0
```

This app requires root access. Grant it to **Extra Dim Toggle** in your root
manager when prompted. There is no Shizuku support or flashable root-manager
module.

## Requirements

- Android 8.0+ (API 26+), built against API 37
- A rooted device with `su` installed and root access granted to the app
- Android Studio (or just the JDK 17 + Gradle wrapper) to build

## Building

```bash
# Debug APK
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Release APK (needs signing config — see below)
./gradlew :app:assembleRelease
```

### Release signing

Create a gitignored `keystore.properties` in the project root:

```properties
storeFile=/path/to/keystore.jks
storePassword=****
keyAlias=****
keyPassword=****
```

The release build picks it up automatically; without it, release signing is
left unset.

## Project structure

```
app/src/main/java/com/extradim/toggle/
├── MainActivity.kt            # Compose UI: status + toggle button
├── ExtraDimController.kt      # Reads/writes the secure setting
├── ExtraDimTileService.kt     # Quick Settings tile
├── ExtraDimWidgetProvider.kt  # Home screen widget
└── RootShell.kt               # Runs privileged commands through su
```

## Notes

- Extra Dim is a system feature; this app only toggles it — it doesn't modify
  any system files.
- The Quick Settings tile and widget also require root access to toggle the
  setting.

## License

[MIT](LICENSE)

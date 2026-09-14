# Extra Dim Toggle

A tiny, rooted-Android utility that flips the system **"Reduce bright colors"**
(Extra Dim) setting with one tap — from the app, a Quick Settings tile, or a
home screen widget.

> **Extra Dim** dims your screen *below* the minimum brightness the slider
> allows — perfect for night reading or saving battery on OLED panels.

## Features

| Surface | What it does |
|---|---|
| 📱 **App** | Compose (Material 3) screen showing the current ON/OFF state with a big toggle button |
| 🔽 **Quick Settings tile** | Pull down the shade, tap the tile to toggle |
| 🏠 **Home screen widget** | One-tap toggle straight from the launcher |

All root work happens off the main thread, so the UI never freezes.

## How it works

The app shells out to `su` and flips the secure setting that backs Extra Dim:

```bash
# ON
su -c 'settings put secure reduce_bright_colors_activated 1'
# OFF
su -c 'settings put secure reduce_bright_colors_activated 0'
```

## KernelSU / Magisk module

The repo also ships a flashable module (`module/`) that:

1. Installs the bundled release APK on flash.
2. Re-installs it on every boot if it's missing (`service.sh`).
3. Cleans up after itself when you remove the module (`uninstall.sh`).

Build the module zip with:

```bash
./gradlew :app:buildKsuModule
# → app/build/distributions/extradim-toggle-module.zip
```

After flashing, open your root manager (KernelSU / Magisk) and grant root to
**Extra Dim Toggle**.

## Requirements

- Android 8.0+ (API 26+), built against API 37
- A **rooted** device with root granted to the app
- Android Studio (or just the JDK 17 + Gradle wrapper) to build

## Building

```bash
# Debug APK
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Release APK (needs signing config — see below)
./gradlew :app:assembleRelease

# Flashable KernelSU module zip (includes the release APK)
./gradlew :app:buildKsuModule
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
├── ExtraDimController.kt      # Reads/writes the secure setting via su
├── ExtraDimTileService.kt     # Quick Settings tile
├── ExtraDimWidgetProvider.kt  # Home screen widget
└── RootShell.kt               # Root shell helper (background threads)

module/
├── module.prop                # Module metadata
├── customize.sh               # Installs APK on flash
├── service.sh                 # Re-installs APK on boot if missing
└── uninstall.sh               # Removes the app when module is removed
```

## Notes

- Extra Dim is a system feature; this app only toggles it — it doesn't modify
  any system files.
- The widget label updates when tapped or on system `onUpdate`. To mirror
  changes made from the tile or elsewhere in real time, a `ContentObserver`
  on the setting could be added.

## License

[MIT](LICENSE)

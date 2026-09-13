# Extra Dim Toggle

Android app (targetSdk 37 / Android 17) that toggles the system
**Reduce bright colors** (Extra Dim) setting via root:

- `su -c 'settings put secure reduce_bright_colors_activated 1'` -> ON
- `su -c 'settings put secure reduce_bright_colors_activated 0'` -> OFF

Includes:
- **Main screen** (Jetpack Compose) with status + toggle button
- **Quick Settings tile** (`TileService`) — tap to toggle
- **Home screen widget** — tap to toggle

## Requirements
- Android Studio with SDK Platform 37 installed
- Device with root access (Magisk/KernelSU etc.)
- Root access granted to the app

## Build
1. Open the project in Android Studio.
2. Let Gradle sync.
3. Run on a rooted device: `./gradlew :app:assembleDebug`
   (APK at `app/build/outputs/apk/debug/app-debug.apk`)

## Notes
- All root operations run on background threads.
- The widget updates its ON/OFF label whenever you tap it or when the
  system fires `onUpdate`. To also reflect changes made from the tile or
  elsewhere automatically, a `ContentObserver` on the setting could be
  added.

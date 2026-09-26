# Minimize kept rules: R8 runs with proguard-android-optimize.txt defaults.
# Compose and AndroidX ship their own consumer rules; nothing extra is
# needed for this app (no reflection, no serialization, no JNI).

# Shizuku user service: keep the AIDL stubs and the service class so the
# server can instantiate it (the bind uses a stable tag, but keep the
# binder internals intact).
-keep class com.extradim.toggle.IShellService { *; }
-keep class com.extradim.toggle.IShellService$* { *; }
-keep class com.extradim.toggle.ShellUserService { *; }

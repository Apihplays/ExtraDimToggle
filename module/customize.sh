#!/system/bin/sh
# Runs at module install time (KernelSU manager shell, uid 0).

APK="$MODPATH/ExtraDimToggle.apk"
PKG="com.extradim.toggle"
TMP="/data/local/tmp/extradim_install.apk"

ui_print "- Installing bundled app ($PKG)..."

# Copy to a world-readable location: installd may be denied direct
# access to files under /data/adb/modules.
cp "$APK" "$TMP" && chmod 644 "$TMP"

if pm install -r "$TMP" >/dev/null 2>&1; then
    ui_print "- App installed. Grant root to it in KernelSU manager."
else
    ui_print "! pm install failed - will retry on next boot (service.sh)"
fi

rm -f "$TMP"
ui_print "- Done. Reboot not required."

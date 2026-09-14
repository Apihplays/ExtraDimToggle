#!/system/bin/sh
# Late-start service: ensures the bundled app is installed on every boot
# (covers first boot after flash, failed installs, and ROM updates).

MODDIR=${0%/*}
APK="$MODDIR/ExtraDimToggle.apk"
PKG="com.extradim.toggle"
TMP="/data/local/tmp/extradim_install.apk"

(
    i=0
    while [ "$i" -lt 60 ]; do
        # Already installed? Nothing to do.
        if [ -n "$(pm path "$PKG" 2>/dev/null)" ]; then
            exit 0
        fi
        sleep 5
        i=$((i + 1))
    done

    # Still missing after ~5 min of waiting: install it.
    if [ -f "$APK" ]; then
        cp "$APK" "$TMP" && chmod 644 "$TMP"
        pm install -r "$TMP" >/dev/null 2>&1
        rm -f "$TMP"
    fi
) &

#!/system/bin/sh
# Remove the app this module installed.
rm -f /data/local/tmp/extradim_install.apk
pm uninstall com.extradim.toggle >/dev/null 2>&1

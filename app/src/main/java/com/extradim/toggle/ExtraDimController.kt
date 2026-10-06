package com.extradim.toggle

import android.content.Context
import android.net.Uri
import android.provider.Settings

/**
 * Controls the system "Reduce bright colors" (Extra Dim) setting:
 *   settings put secure reduce_bright_colors_activated 0/1
 */
object ExtraDimController {

    private const val SETTING_KEY = "reduce_bright_colors_activated"

    /** Serializes read-then-write so concurrent surfaces can't double-flip. */
    private val toggleLock = Any()

    val SETTING_URI: Uri = Settings.Secure.getUriFor(SETTING_KEY)

    fun setEnabled(enabled: Boolean): Boolean {
        val value = if (enabled) "1" else "0"
        return RootShell.run("settings put secure $SETTING_KEY $value")
    }

    /** Native ContentResolver read: instant, zero root/IPC process overhead. */
    fun isEnabled(context: Context): Boolean =
        Settings.Secure.getInt(context.contentResolver, SETTING_KEY, 0) == 1

    fun toggle(context: Context): Boolean = synchronized(toggleLock) {
        val nowEnabled = isEnabled(context)
        if (setEnabled(!nowEnabled)) !nowEnabled else nowEnabled
    }
}

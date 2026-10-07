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

    /**
     * Reads the setting via root shell.
     * Note: On Android 12+ (S+), reduce_bright_colors_activated is an @hide key
     * not annotated with @Readable, so calling Settings.Secure.getInt from a non-system
     * app throws SecurityException.
     */
    fun isEnabled(): Boolean {
        return RootShell.runWithOutput("settings get secure $SETTING_KEY") == "1"
    }

    /** Overload for compatibility; always delegates to root shell read. */
    fun isEnabled(context: Context): Boolean = isEnabled()

    fun toggle(context: Context? = null): Boolean = synchronized(toggleLock) {
        val nowEnabled = isEnabled()
        if (setEnabled(!nowEnabled)) !nowEnabled else nowEnabled
    }
}

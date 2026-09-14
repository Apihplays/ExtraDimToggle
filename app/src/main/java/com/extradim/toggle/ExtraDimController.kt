package com.extradim.toggle

/**
 * Controls the system "Reduce bright colors" (Extra Dim) setting:
 *   settings put secure reduce_bright_colors_activated 0/1
 */
object ExtraDimController {

    private const val SETTING_KEY = "reduce_bright_colors_activated"

    fun setEnabled(enabled: Boolean): Boolean {
        val value = if (enabled) "1" else "0"
        return RootShell.run("settings put secure $SETTING_KEY $value")
    }

    fun isEnabled(): Boolean {
        return RootShell.runWithOutput("settings get secure $SETTING_KEY") == "1"
    }

    fun toggle(): Boolean {
        val nowEnabled = isEnabled()
        return if (setEnabled(!nowEnabled)) !nowEnabled else nowEnabled
    }
}

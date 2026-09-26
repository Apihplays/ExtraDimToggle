package com.extradim.toggle

import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Minimal helper to run a single command as root, or — when root is not
 * available — through Shizuku (shell uid), which can write the secure
 * setting this app toggles.
 *
 * Hardening:
 * - stdout and stderr are drained concurrently (prevents pipe-buffer deadlock)
 * - commands are serialized so only one privileged session runs at a time
 *   (avoids stacked superuser prompts)
 * - a watchdog destroys hung shells (e.g. an unanswered su prompt)
 * - backend (root vs Shizuku) is cached after the first successful probe
 */
object RootShell {

    private const val TIMEOUT_SECONDS = 30L
    private val lock = Any()

    /** Which privileged backend produced the last successful command. */
    @Volatile
    var activeBackend: Backend = Backend.UNKNOWN
        private set

    enum class Backend { ROOT, SHIZUKU, UNKNOWN }

    /** Returns true if the command exited with code 0. */
    fun run(command: String): Boolean = exec(command) != null

    /** Executes the command and returns stdout (trimmed), or null on failure. */
    fun runWithOutput(command: String): String? = exec(command)

    /** Quick check that privileged execution is available (root or Shizuku). */
    fun isAvailable(): Boolean = probe() != null

    /**
     * Probes which backend works, preferring root. Returns the backend name
     * or null if neither is usable. Never pops a Shizuku permission dialog;
     * use [requestShizukuPermission] for that.
     */
    fun probe(): Backend? = when {
        exec("id -u", Backend.ROOT) == "0" -> Backend.ROOT
        ShizukuShell.isAvailable() -> Backend.SHIZUKU
        else -> null
    }.also { if (it != null) activeBackend = it }

    /** True when root specifically is usable (su present and granting). */
    fun isRootAvailable(): Boolean = probe() == Backend.ROOT

    /**
     * True when Shizuku is installed, running, and has already been granted
     * permission to this app. Never blocks on UI.
     */
    fun isShizukuReady(): Boolean = ShizukuShell.isAvailable()

    /** Launches Shizuku's permission request flow. */
    fun requestShizukuPermission() {
        ShizukuShell.requestPermission()
    }

    private fun exec(command: String): String? = exec(command, null)

    private fun exec(command: String, preferred: Backend?): String? {
        synchronized(lock) {
            // Explicit backend choice (used by probe()).
            if (preferred != null) {
                val out = runOn(backend = preferred, command = command)
                if (out != null) activeBackend = preferred
                return out
            }
            // Fast path: keep using whichever backend worked before.
            val cached = activeBackend
            if (cached != Backend.UNKNOWN) {
                val out = runOn(backend = cached, command = command)
                if (out != null) return out
                activeBackend = Backend.UNKNOWN // cached backend died; re-probe
            }
            // No working backend cached: probe root first, then Shizuku.
            runOn(Backend.ROOT, command)?.let {
                activeBackend = Backend.ROOT
                return it
            }
            runOn(Backend.SHIZUKU, command)?.let {
                activeBackend = Backend.SHIZUKU
                return it
            }
            return null
        }
    }

    private fun runOn(backend: Backend, command: String): String? = when (backend) {
        Backend.ROOT -> execVia("su", command)
        Backend.SHIZUKU -> ShizukuShell.exec(command)
        Backend.UNKNOWN -> null
    }

    private fun execVia(shell: String, command: String): String? {
        var process: Process? = null
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf(shell, "-c", command))
            process = proc

            val stdout = StringBuilder()
            // Drain both pipes while the process runs; an unread pipe can
            // fill up and block the child process forever.
            val drainOut = Thread {
                proc.inputStream.bufferedReader().forEachLine { stdout.appendLine(it) }
            }
            val drainErr = Thread {
                proc.errorStream.bufferedReader().forEachLine { /* drained & discarded */ }
            }
            drainOut.start()
            drainErr.start()

            val finished = proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return null
            }
            drainOut.join(1_000)
            drainErr.join(1_000)

            if (proc.exitValue() == 0) stdout.toString().trim() else null
        } catch (e: IOException) {
            null
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            process?.destroyForcibly()
            null
        } finally {
            process?.destroy()
        }
    }
}

package com.extradim.toggle

import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Minimal helper to run a single command as root.
 *
 * Hardening:
 * - stdout and stderr are drained concurrently (prevents pipe-buffer deadlock)
 * - commands are serialized so only one `su` session runs at a time
 *   (avoids stacked superuser prompts)
 * - a watchdog destroys hung shells (e.g. an unanswered su prompt)
 */
object RootShell {

    private const val TIMEOUT_SECONDS = 30L
    private val lock = Any()

    /** Returns true if the command exited with code 0. */
    fun run(command: String): Boolean = exec(command) != null

    /** Executes the command and returns stdout (trimmed), or null on failure. */
    fun runWithOutput(command: String): String? = exec(command)

    /** Quick check that su is available and grants root. */
    fun isRootAvailable(): Boolean = runWithOutput("id -u") == "0"

    private fun exec(command: String): String? {
        synchronized(lock) {
            var process: Process? = null
            return try {
                val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
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
}

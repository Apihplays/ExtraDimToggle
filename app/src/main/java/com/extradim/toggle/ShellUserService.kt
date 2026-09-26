package com.extradim.toggle

import java.util.concurrent.TimeUnit

/**
 * Shizuku User Service: this code runs in a separate process owned by the
 * Shizuku server, as the server's uid (shell 2000 via adb, or root).
 *
 * It exposes a single synchronous binder call that runs a command and
 * returns its stdout, or null on any failure. Kept deliberately tiny —
 * no Context usage (most Context APIs do not work in a user service).
 */
class ShellUserService : IShellService.Stub() {

    override fun exec(command: String?): String? {
        if (command == null) return null
        var process: Process? = null
        return try {
            val proc = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(false)
                .start()
            process = proc

            val stdout = StringBuilder()
            val drainOut = Thread {
                proc.inputStream.bufferedReader().forEachLine { stdout.appendLine(it) }
            }
            val drainErr = Thread {
                proc.errorStream.bufferedReader().forEachLine { /* discarded */ }
            }
            drainOut.start()
            drainErr.start()

            if (!proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                proc.destroyForcibly()
                return null
            }
            drainOut.join(1_000)
            drainErr.join(1_000)

            if (proc.exitValue() == 0) stdout.toString().trim() else null
        } catch (e: Exception) {
            null
        } finally {
            process?.destroy()
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 30L
    }
}

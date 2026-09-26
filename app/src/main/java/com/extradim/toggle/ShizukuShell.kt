package com.extradim.toggle

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Shizuku backend: runs commands with shell (adb) or root uid through a
 * Shizuku/Sui [user service][ShellUserService].
 *
 * Design notes:
 * - [init] must be called once per process before [exec] (tile, widget and
 *   activity all call it) so we have a Context for the ComponentName.
 * - All methods are safe from any thread but expected to run off the main
 *   thread; [exec] blocks until the command finishes or times out.
 * - Every failure mode (not installed, server not running, permission not
 *   granted, bind failed, command failed) collapses to null from [exec].
 * - Permission is never requested implicitly from background surfaces
 *   (tile/widget); only [requestPermission] pops the dialog, and it is
 *   only invoked from the activity.
 */
object ShizukuShell {

    const val REQUEST_CODE = 7001

    /** Bump when ShellUserService's code changes so the server restarts it. */
    private const val SERVICE_VERSION = 1
    private const val SERVICE_TAG = "ShellUserService"
    private const val BIND_TIMEOUT_MS = 15_000L

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var service: IShellService? = null

    private val bindLock = Any()

    /** Cache a context for building the service ComponentName. Idempotent. */
    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    /** True when the Shizuku (or Sui) binder is alive and usable. */
    fun isBinderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Throwable) {
        false // API not initialized / Shizuku not installed
    }

    /** True when this app is allowed to use Shizuku. */
    fun isGranted(): Boolean = try {
        !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    /** Binder alive + permission granted => commands can actually run. */
    fun isAvailable(): Boolean = isBinderAlive() && isGranted()

    /** Launches Shizuku's runtime-permission dialog. Call from the UI. */
    fun requestPermission() {
        try {
            if (Shizuku.isPreV11()) return // unsupported, ignore
            if (Shizuku.shouldShowRequestPermissionRationale()) return // permanently denied
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: Throwable) {
            // Shizuku gone mid-request; nothing sensible to do here.
        }
    }

    /** Runs the command as shell/root uid via the user service. */
    fun exec(command: String): String? {
        val svc = obtainService() ?: return null
        return try {
            svc.exec(command)
        } catch (e: Exception) {
            // Remote process died; drop the cache so the next call re-binds.
            service = null
            null
        }
    }

    private fun obtainService(): IShellService? {
        service?.let { return it }
        synchronized(bindLock) {
            service?.let { return it }
            if (!isAvailable()) return null
            val context = appContext ?: return null

            var connected: IShellService? = null
            val latch = CountDownLatch(1)
            val args = Shizuku.UserServiceArgs(
                ComponentName(context, ShellUserService::class.java)
            )
                .version(SERVICE_VERSION)
                // Stable tag: R8 may rename the class, the server keys on this.
                .tag(SERVICE_TAG)
                .daemon(false) // live only while our process lives; re-bind on demand

            val connection = object : android.content.ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    connected = IShellService.Stub.asInterface(binder)
                    latch.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    service = null
                }
            }

            return try {
                Shizuku.bindUserService(args, connection)
                if (latch.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    connected.also { service = it }
                } else {
                    null
                }
            } catch (e: Throwable) {
                null
            }
        }
    }
}

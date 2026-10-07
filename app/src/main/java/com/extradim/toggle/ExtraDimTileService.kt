package com.extradim.toggle

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick Settings tile: tap to toggle Extra Dim on/off.
 *
 * Uses native ContentResolver read, ContentObserver for real-time sync,
 * and Coroutines off the main thread for privileged writes.
 */
class ExtraDimTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var settingObserver: ContentObserver? = null
    private var toggleJob: kotlinx.coroutines.Job? = null

    override fun onStartListening() {
        // Register ContentObserver to mirror changes made from system settings or widget
        if (settingObserver == null) {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    syncState()
                    ExtraDimWidgetProvider.updateAll(this@ExtraDimTileService)
                }
            }
            try {
                contentResolver.registerContentObserver(
                    ExtraDimController.SETTING_URI,
                    false,
                    observer
                )
                settingObserver = observer
            } catch (e: Exception) {
                // Ignore observer registration failures if permission is restricted
            }
        }
        syncState()
    }

    override fun onStopListening() {
        settingObserver?.let {
            try {
                contentResolver.unregisterContentObserver(it)
            } catch (e: Exception) {
            }
            settingObserver = null
        }
    }

    override fun onClick() {
        // Optimistic UI update: instantly flip tile state on tap
        val currentTile = qsTile
        val target = if (currentTile != null) currentTile.state != Tile.STATE_ACTIVE else null
        if (target != null) {
            applyTileState(target)
        }

        toggleJob?.cancel()
        toggleJob = serviceScope.launch {
            kotlinx.coroutines.delay(40)
            val success = withContext(Dispatchers.IO) {
                if (target != null) {
                    ExtraDimController.setEnabled(target)
                } else {
                    ExtraDimController.toggle(this@ExtraDimTileService)
                }
            }
            if (!success && target != null) {
                // Revert if setting failed
                applyTileState(!target)
            }
            ExtraDimWidgetProvider.updateAll(this@ExtraDimTileService)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun syncState() {
        serviceScope.launch {
            val enabled = withContext(Dispatchers.IO) {
                ExtraDimController.isEnabled()
            }
            applyTileState(enabled)
        }
    }

    private fun applyTileState(enabled: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.contentDescription = tile.label
        tile.updateTile()
    }
}

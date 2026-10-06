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
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                ExtraDimController.toggle(this@ExtraDimTileService)
            }
            // ContentObserver handles tile state and widget update reactively
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun syncState() {
        // Native ContentResolver read is synchronous and fast (no root/process required)
        val enabled = ExtraDimController.isEnabled(this)
        applyTileState(enabled)
    }

    private fun applyTileState(enabled: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.contentDescription = tile.label
        tile.updateTile()
    }
}

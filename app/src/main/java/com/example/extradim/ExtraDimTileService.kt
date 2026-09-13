package com.example.extradim

import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile: tap to toggle Extra Dim on/off.
 *
 * Root shell calls never run on the main thread: state is read on a
 * background thread, then the tile is updated on the main thread.
 */
class ExtraDimTileService : TileService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        refreshTileAsync()
    }

    override fun onClick() {
        Thread {
            ExtraDimController.toggle()
            refreshTileAsync()
        }.start()
    }

    /**
     * Reads the (slow, root-shelled) setting off the main thread, then
     * applies the result to the tile on the main thread.
     */
    private fun refreshTileAsync() {
        Thread {
            val enabled = ExtraDimController.isEnabled()
            mainHandler.post {
                val tile = qsTile ?: return@post  // service not listening anymore
                tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                tile.label = getString(R.string.tile_label)
                tile.contentDescription = tile.label
                tile.updateTile()
            }
        }.start()
    }
}

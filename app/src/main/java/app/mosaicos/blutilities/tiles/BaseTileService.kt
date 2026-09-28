package app.mosaicos.blutilities.tiles

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

abstract class BaseTileService : TileService() {

    /** Return false to mark the tile as STATE_UNAVAILABLE. */
    abstract fun isAvailable(): Boolean

    /** Return true for STATE_ACTIVE, false for STATE_INACTIVE. */
    abstract fun isActive(): Boolean

    abstract fun onTileClicked()

    /** Called before the tile state is applied, for subtitle and similar updates. */
    open fun onBeforeRefresh() {}

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        if (!isAvailable()) return
        onTileClicked()
    }

    fun refreshTile() {
        onBeforeRefresh()
        val tile = qsTile ?: return
        tile.state = when {
            !isAvailable() -> Tile.STATE_UNAVAILABLE
            isActive() -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }
}

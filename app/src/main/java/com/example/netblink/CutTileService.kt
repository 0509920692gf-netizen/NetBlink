package com.example.netblink

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** بلاط في الإعدادات السريعة: لمسة واحدة لقطع الاتصال وإعادته. */
class CutTileService : TileService() {

    override fun onStartListening() {
        setState(NetController.busy.get())
    }

    override fun onClick() {
        if (!NetController.shizukuReady()) return
        setState(true)
        NetController.cut(this) { setState(false) }
    }

    private fun setState(active: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}

package io.github.mority.gloomy

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import java.lang.ref.WeakReference

/**
 * Quick settings tile that turns gloomy on and off, and shows as active while it is open.
 * Since [MainActivity] shows over the lock screen, this works without unlocking the phone.
 */
class GloomyTileService : TileService() {
    companion object {
        private var running: WeakReference<GloomyTileService>? = null

        /**
         * Shows on the tile whether gloomy is open, if the tile service is running. Refreshing
         * only in [onStartListening] isn't enough: after a tap, System UI may skip it the next
         * time quick settings open, and show the state from the tap.
         */
        fun update() {
            running?.get()?.showActive(MainActivity.open != null)
        }
    }

    override fun onCreate() {
        super.onCreate()
        running = WeakReference(this)
    }

    override fun onDestroy() {
        if (running?.get() === this) running = null
        super.onDestroy()
    }

    override fun onStartListening() = showActive(MainActivity.open != null)

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        // Quick settings stay open on top of gloomy, like for other toggles.
        MainActivity.open?.let {
            it.finish()
            showActive(false)
            return
        }

        showActive(true)
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun showActive(active: Boolean) {
        qsTile?.apply {
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}

package io.github.mority.gloomy

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.abs

/** Color stops, evenly spaced over the color range: red -> amber -> yellow -> white. */
private val COLOR_STOPS = listOf(
    Color(0xFFFF0000), // red
    Color(0xFFFFBF00), // amber
    Color(0xFFFFFF00), // yellow
    Color.White,
)

/** Lowest backlight value; 0 switches the backlight off entirely on some devices. */
private const val MIN_BACKLIGHT = 0.01f

/** Color intensity at level 0, so the dimmest setting goes below the hardware minimum. */
private const val MIN_COLOR_SCALE = 0.05f

/** How many screen widths a horizontal swipe needs to cover the whole color range. */
private const val COLOR_RANGE_WIDTHS = 1.5f

/** Brightness change per volume key press. */
private const val VOLUME_KEY_STEP = 0.05f

private const val KEY_COLOR = "color_position"
private const val KEY_LEVEL = "level"

class MainActivity : ComponentActivity() {
    /** Position in the color range [0, 1]: red at 0, through amber and yellow, to white at 1. */
    private var colorPosition by mutableFloatStateOf(0f)

    /** Brightness level in [0, 1]. */
    private var level by mutableFloatStateOf(0.15f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getPreferences(MODE_PRIVATE)
        colorPosition = prefs.getFloat(KEY_COLOR, 0f).coerceIn(0f, 1f)
        level = prefs.getFloat(KEY_LEVEL, 0.15f).coerceIn(0f, 1f)

        // From API 27 on, android:showWhenLocked in the manifest lets this show over the
        // lock screen. It has to be there rather than set here: System UI reads it from
        // the manifest to decide whether a tile tap needs unlocking first.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }

        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            // Drive the real backlight from the level. This overrides the system
            // brightness only while this window is in the foreground.
            LaunchedEffect(level) {
                window.attributes = window.attributes.apply {
                    screenBrightness = level.coerceAtLeast(MIN_BACKLIGHT)
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .background(dim(colorAt(colorPosition), level))
                    .pointerInput(Unit) { detectSwipes() }
            )
        }
    }

    /** Volume up/down step the brightness like a swipe; holding a key repeats. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val step = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> VOLUME_KEY_STEP
            KeyEvent.KEYCODE_VOLUME_DOWN -> -VOLUME_KEY_STEP
            else -> return super.onKeyDown(keyCode, event)
        }
        level = (level + step).coerceIn(0f, 1f)
        return true
    }

    override fun onStop() {
        super.onStop()
        getPreferences(MODE_PRIVATE).edit {
            putFloat(KEY_COLOR, colorPosition)
            putFloat(KEY_LEVEL, level)
        }
    }

    /**
     * Each drag locks to the axis it starts out on: vertical changes the
     * brightness (a full-height swipe covers the whole range), horizontal
     * moves along the color range.
     */
    private suspend fun PointerInputScope.detectSwipes() {
        var horizontal = false
        var decided = false

        detectDragGestures(
            onDragStart = { decided = false },
        ) { change, drag ->
            change.consume()
            if (!decided) {
                horizontal = abs(drag.x) > abs(drag.y)
                decided = true
            }

            if (horizontal) {
                // Swiping left moves towards yellow and white, right back towards red.
                val delta = -drag.x / (size.width * COLOR_RANGE_WIDTHS)
                colorPosition = (colorPosition + delta).coerceIn(0f, 1f)
            } else {
                // Screen y grows downwards, so swiping up (negative dy) brightens.
                level = (level - drag.y / size.height).coerceIn(0f, 1f)
            }
        }
    }
}

/** Maps a position in [0, 1] to a color by interpolating between [COLOR_STOPS]. */
private fun colorAt(position: Float): Color {
    val scaled = position * (COLOR_STOPS.size - 1)
    val i = scaled.toInt().coerceAtMost(COLOR_STOPS.size - 2)
    return lerp(COLOR_STOPS[i], COLOR_STOPS[i + 1], scaled - i)
}

/** Scales [color] so that level 0 still shows a faint glow and level 1 is the full color. */
private fun dim(color: Color, level: Float): Color {
    val f = MIN_COLOR_SCALE + (1f - MIN_COLOR_SCALE) * level
    return Color(color.red * f, color.green * f, color.blue * f)
}

package com.android.daw.ui.theme

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path

/**
 * SD Studio DAW Pure Kotlin Vector Icons
 *
 * All icons are 100% Kotlin ImageVector definitions.
 * Zero XML drawables, zero external font glyphs.
 */
object DawIcons {

    private inline fun buildIcon(
        name: String,
        crossinline block: PathBuilder.() -> Unit
    ): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = Space.iconMin,
            defaultHeight = Space.iconMin,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Palette.InkOnDark),
            pathBuilder = { block() }
        ).build()
    }

    // 1. Play
    val Play: ImageVector by lazy {
        buildIcon("Play") {
            moveTo(8f, 5.14f)
            lineTo(19f, 12f)
            lineTo(8f, 18.86f)
            close()
        }
    }

    // 2. Pause
    val Pause: ImageVector by lazy {
        buildIcon("Pause") {
            moveTo(6f, 5f)
            lineTo(10f, 5f)
            lineTo(10f, 19f)
            lineTo(6f, 19f)
            close()
            moveTo(14f, 5f)
            lineTo(18f, 5f)
            lineTo(18f, 19f)
            lineTo(14f, 19f)
            close()
        }
    }

    // 3. Stop
    val Stop: ImageVector by lazy {
        buildIcon("Stop") {
            moveTo(6f, 6f)
            lineTo(18f, 6f)
            lineTo(18f, 18f)
            lineTo(6f, 18f)
            close()
        }
    }

    // 4. Record
    val Record: ImageVector by lazy {
        buildIcon("Record") {
            moveTo(12f, 6f)
            curveTo(15.31f, 6f, 18f, 8.69f, 18f, 12f)
            curveTo(18f, 15.31f, 15.31f, 18f, 12f, 18f)
            curveTo(8.69f, 18f, 6f, 15.31f, 6f, 12f)
            curveTo(6f, 8.69f, 8.69f, 6f, 12f, 6f)
            close()
        }
    }

    // 5. Loop
    val Loop: ImageVector by lazy {
        buildIcon("Loop") {
            moveTo(7f, 7f)
            lineTo(17f, 7f)
            lineTo(17f, 10f)
            lineTo(21f, 6f)
            lineTo(17f, 2f)
            lineTo(17f, 5f)
            lineTo(5f, 5f)
            lineTo(5f, 11f)
            lineTo(7f, 11f)
            close()
            moveTo(17f, 17f)
            lineTo(7f, 17f)
            lineTo(7f, 14f)
            lineTo(3f, 18f)
            lineTo(7f, 22f)
            lineTo(7f, 19f)
            lineTo(19f, 19f)
            lineTo(19f, 13f)
            lineTo(17f, 13f)
            close()
        }
    }

    // 6. Reverse
    val Reverse: ImageVector by lazy {
        buildIcon("Reverse") {
            moveTo(11f, 12f)
            lineTo(20f, 6f)
            lineTo(20f, 18f)
            close()
            moveTo(3f, 12f)
            lineTo(12f, 6f)
            lineTo(12f, 18f)
            close()
        }
    }

    // 7. Mixer
    val Mixer: ImageVector by lazy {
        buildIcon("Mixer") {
            // Track 1
            moveTo(5f, 3f)
            lineTo(7f, 3f)
            lineTo(7f, 7f)
            lineTo(9f, 7f)
            lineTo(9f, 11f)
            lineTo(7f, 11f)
            lineTo(7f, 21f)
            lineTo(5f, 21f)
            lineTo(5f, 11f)
            lineTo(3f, 11f)
            lineTo(3f, 7f)
            lineTo(5f, 7f)
            close()
            // Track 2
            moveTo(11f, 3f)
            lineTo(13f, 3f)
            lineTo(13f, 13f)
            lineTo(15f, 13f)
            lineTo(15f, 17f)
            lineTo(13f, 17f)
            lineTo(13f, 21f)
            lineTo(11f, 21f)
            lineTo(11f, 17f)
            lineTo(9f, 17f)
            lineTo(9f, 13f)
            lineTo(11f, 13f)
            close()
            // Track 3
            moveTo(17f, 3f)
            lineTo(19f, 3f)
            lineTo(19f, 9f)
            lineTo(21f, 9f)
            lineTo(21f, 13f)
            lineTo(19f, 13f)
            lineTo(19f, 21f)
            lineTo(17f, 21f)
            lineTo(17f, 13f)
            lineTo(15f, 13f)
            lineTo(15f, 9f)
            lineTo(17f, 9f)
            close()
        }
    }

    // 8. PianoRoll
    val PianoRoll: ImageVector by lazy {
        buildIcon("PianoRoll") {
            moveTo(19f, 3f)
            lineTo(5f, 3f)
            curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
            lineTo(3f, 19f)
            curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
            lineTo(19f, 21f)
            curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
            lineTo(21f, 5f)
            curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
            close()
            // Keys
            moveTo(11f, 13f)
            lineTo(11f, 5f)
            lineTo(13f, 5f)
            lineTo(13f, 13f)
            lineTo(14.5f, 13f)
            lineTo(14.5f, 19f)
            lineTo(9.5f, 19f)
            lineTo(9.5f, 13f)
            close()
            moveTo(5f, 5f)
            lineTo(7f, 5f)
            lineTo(7f, 13f)
            lineTo(8.5f, 13f)
            lineTo(8.5f, 19f)
            lineTo(5f, 19f)
            close()
            moveTo(19f, 19f)
            lineTo(15.5f, 19f)
            lineTo(15.5f, 13f)
            lineTo(17f, 13f)
            lineTo(17f, 5f)
            lineTo(19f, 5f)
            close()
        }
    }

    // 9. Add
    val Add: ImageVector by lazy {
        buildIcon("Add") {
            moveTo(19f, 11f)
            lineTo(13f, 11f)
            lineTo(13f, 5f)
            lineTo(11f, 5f)
            lineTo(11f, 11f)
            lineTo(5f, 11f)
            lineTo(5f, 13f)
            lineTo(11f, 13f)
            lineTo(11f, 19f)
            lineTo(13f, 19f)
            lineTo(13f, 13f)
            lineTo(19f, 13f)
            close()
        }
    }

    // 10. Settings
    val Settings: ImageVector by lazy {
        buildIcon("Settings") {
            moveTo(19.14f, 12.94f)
            curveTo(19.18f, 12.63f, 19.2f, 12.32f, 19.2f, 12f)
            curveTo(19.2f, 11.68f, 19.18f, 11.37f, 19.14f, 11.06f)
            lineTo(21.41f, 9.29f)
            curveTo(21.62f, 9.13f, 21.67f, 8.83f, 21.54f, 8.6f)
            lineTo(19.39f, 4.88f)
            curveTo(19.26f, 4.65f, 18.98f, 4.56f, 18.74f, 4.65f)
            lineTo(16.07f, 5.73f)
            curveTo(15.52f, 5.31f, 14.91f, 4.96f, 14.25f, 4.7f)
            lineTo(13.85f, 1.86f)
            curveTo(13.81f, 1.61f, 13.59f, 1.43f, 13.34f, 1.43f)
            lineTo(9.06f, 1.43f)
            curveTo(8.81f, 1.43f, 8.6f, 1.61f, 8.56f, 1.86f)
            lineTo(8.16f, 4.7f)
            curveTo(7.5f, 4.96f, 6.89f, 5.31f, 6.33f, 5.73f)
            lineTo(3.66f, 4.65f)
            curveTo(3.42f, 4.56f, 3.14f, 4.65f, 3.01f, 4.88f)
            lineTo(0.86f, 8.6f)
            curveTo(0.73f, 8.83f, 0.78f, 9.13f, 0.99f, 9.29f)
            lineTo(3.26f, 11.06f)
            curveTo(3.22f, 11.37f, 3.2f, 11.69f, 3.2f, 12f)
            curveTo(3.2f, 12.31f, 3.22f, 12.63f, 3.26f, 12.94f)
            lineTo(0.99f, 14.71f)
            curveTo(0.78f, 14.87f, 0.73f, 15.17f, 0.86f, 15.4f)
            lineTo(3.01f, 19.12f)
            curveTo(3.14f, 19.35f, 3.42f, 19.44f, 3.66f, 19.35f)
            lineTo(6.33f, 18.27f)
            curveTo(6.89f, 18.69f, 7.5f, 19.04f, 8.16f, 19.3f)
            lineTo(8.56f, 22.14f)
            curveTo(8.6f, 22.39f, 8.81f, 22.57f, 9.06f, 22.57f)
            lineTo(13.34f, 22.57f)
            curveTo(13.59f, 22.57f, 13.81f, 22.39f, 13.85f, 22.14f)
            lineTo(14.25f, 19.3f)
            curveTo(14.91f, 19.04f, 15.52f, 18.69f, 16.07f, 18.27f)
            lineTo(18.74f, 19.35f)
            curveTo(18.98f, 19.44f, 19.26f, 19.35f, 19.39f, 19.12f)
            lineTo(21.54f, 15.4f)
            curveTo(21.67f, 15.17f, 21.62f, 14.87f, 21.41f, 14.71f)
            lineTo(19.14f, 12.94f)
            close()
            moveTo(11.2f, 15.5f)
            curveTo(9.27f, 15.5f, 7.7f, 13.93f, 7.7f, 12f)
            curveTo(7.7f, 10.07f, 9.27f, 8.5f, 11.2f, 8.5f)
            curveTo(13.13f, 8.5f, 14.7f, 10.07f, 14.7f, 12f)
            curveTo(14.7f, 13.93f, 13.13f, 15.5f, 11.2f, 15.5f)
            close()
        }
    }

    // 11. Back
    val Back: ImageVector by lazy {
        buildIcon("Back") {
            moveTo(20f, 11f)
            lineTo(7.83f, 11f)
            lineTo(13.42f, 5.41f)
            lineTo(12f, 4f)
            lineTo(4f, 12f)
            lineTo(12f, 20f)
            lineTo(13.41f, 18.59f)
            lineTo(7.83f, 13f)
            lineTo(20f, 13f)
            close()
        }
    }

    // 12. Forward
    val Forward: ImageVector by lazy {
        buildIcon("Forward") {
            moveTo(4f, 11f)
            lineTo(16.17f, 11f)
            lineTo(10.58f, 5.41f)
            lineTo(12f, 4f)
            lineTo(20f, 12f)
            lineTo(12f, 20f)
            lineTo(10.59f, 18.59f)
            lineTo(16.17f, 13f)
            lineTo(4f, 13f)
            close()
        }
    }

    // 13. Menu
    val Menu: ImageVector by lazy {
        buildIcon("Menu") {
            moveTo(3f, 6f)
            lineTo(21f, 6f)
            lineTo(21f, 8f)
            lineTo(3f, 8f)
            close()
            moveTo(3f, 11f)
            lineTo(21f, 11f)
            lineTo(21f, 13f)
            lineTo(3f, 13f)
            close()
            moveTo(3f, 16f)
            lineTo(21f, 16f)
            lineTo(21f, 18f)
            lineTo(3f, 18f)
            close()
        }
    }

    // 14. Close
    val Close: ImageVector by lazy {
        buildIcon("Close") {
            moveTo(19f, 6.41f)
            lineTo(17.59f, 5f)
            lineTo(12f, 10.59f)
            lineTo(6.41f, 5f)
            lineTo(5f, 6.41f)
            lineTo(10.59f, 12f)
            lineTo(5f, 17.59f)
            lineTo(6.41f, 19f)
            lineTo(12f, 13.41f)
            lineTo(17.59f, 19f)
            lineTo(19f, 17.59f)
            lineTo(13.41f, 12f)
            close()
        }
    }

    // 15. Vol
    val Vol: ImageVector by lazy {
        buildIcon("Vol") {
            moveTo(3f, 9f)
            lineTo(7f, 9f)
            lineTo(12f, 4f)
            lineTo(12f, 20f)
            lineTo(7f, 15f)
            lineTo(3f, 15f)
            close()
            moveTo(16.5f, 12f)
            curveTo(16.5f, 10.23f, 15.48f, 8.71f, 14f, 7.97f)
            lineTo(14f, 16.02f)
            curveTo(15.48f, 15.29f, 16.5f, 13.77f, 16.5f, 12f)
            close()
            moveTo(14f, 3.23f)
            lineTo(14f, 5.29f)
            curveTo(16.89f, 6.15f, 19f, 8.83f, 19f, 12f)
            curveTo(19f, 15.17f, 16.89f, 17.85f, 14f, 18.71f)
            lineTo(14f, 20.77f)
            curveTo(18.01f, 19.86f, 21f, 16.28f, 21f, 12f)
            curveTo(21f, 7.72f, 18.01f, 4.14f, 14f, 3.23f)
            close()
        }
    }

    // 16. Pan
    val Pan: ImageVector by lazy {
        buildIcon("Pan") {
            // Dial knob ring
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
            close()
            moveTo(12f, 20f)
            curveTo(7.59f, 20f, 4f, 16.41f, 4f, 12f)
            curveTo(4f, 7.59f, 7.59f, 4f, 12f, 4f)
            curveTo(16.41f, 4f, 20f, 7.59f, 20f, 12f)
            curveTo(20f, 16.41f, 16.41f, 20f, 12f, 20f)
            close()
            // Center indicator tick
            moveTo(11f, 5f)
            lineTo(13f, 5f)
            lineTo(13f, 12f)
            lineTo(11f, 12f)
            close()
        }
    }

    // 17. Eq
    val Eq: ImageVector by lazy {
        buildIcon("Eq") {
            // EQ frequency bars
            moveTo(3f, 13f)
            lineTo(6f, 13f)
            lineTo(6f, 19f)
            lineTo(3f, 19f)
            close()
            moveTo(8f, 7f)
            lineTo(11f, 7f)
            lineTo(11f, 19f)
            lineTo(8f, 19f)
            close()
            moveTo(13f, 10f)
            lineTo(16f, 10f)
            lineTo(16f, 19f)
            lineTo(13f, 19f)
            close()
            moveTo(18f, 4f)
            lineTo(21f, 4f)
            lineTo(21f, 19f)
            lineTo(18f, 19f)
            close()
        }
    }

    // 18. Delay
    val Delay: ImageVector by lazy {
        buildIcon("Delay") {
            // Clock with echo waves
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
            close()
            moveTo(12f, 20f)
            curveTo(7.58f, 20f, 4f, 16.42f, 4f, 12f)
            curveTo(4f, 7.58f, 7.58f, 4f, 12f, 4f)
            curveTo(16.42f, 4f, 20f, 7.58f, 20f, 12f)
            curveTo(20f, 16.42f, 16.42f, 20f, 12f, 20f)
            close()
            // Hands
            moveTo(12.5f, 7f)
            lineTo(11f, 7f)
            lineTo(11f, 13f)
            lineTo(16.25f, 16.15f)
            lineTo(17f, 14.92f)
            lineTo(12.5f, 12.25f)
            close()
        }
    }

    // 19. Limiter
    val Limiter: ImageVector by lazy {
        buildIcon("Limiter") {
            // Ceiling line at top
            moveTo(3f, 4f)
            lineTo(21f, 4f)
            lineTo(21f, 6f)
            lineTo(3f, 6f)
            close()
            // Clipped waveform
            moveTo(3f, 18f)
            lineTo(7f, 18f)
            lineTo(9f, 8f)
            lineTo(15f, 8f)
            lineTo(17f, 18f)
            lineTo(21f, 18f)
            lineTo(21f, 20f)
            lineTo(3f, 20f)
            close()
        }
    }

    // 20. Track
    val Track: ImageVector by lazy {
        buildIcon("Track") {
            // Track lanes with audio clip blocks
            moveTo(3f, 5f)
            lineTo(21f, 5f)
            lineTo(21f, 7f)
            lineTo(3f, 7f)
            close()
            // Clip 1
            moveTo(5f, 9f)
            lineTo(13f, 9f)
            lineTo(13f, 15f)
            lineTo(5f, 15f)
            close()
            // Clip 2
            moveTo(15f, 9f)
            lineTo(20f, 9f)
            lineTo(20f, 15f)
            lineTo(15f, 15f)
            close()
            // Bottom line
            moveTo(3f, 17f)
            lineTo(21f, 17f)
            lineTo(21f, 19f)
            lineTo(3f, 19f)
            close()
        }
    }

    // 21. ChevronRight
    val ChevronRight: ImageVector by lazy {
        buildIcon("ChevronRight") {
            moveTo(9.29f, 6.71f)
            lineTo(10.71f, 5.29f)
            lineTo(17.41f, 12f)
            lineTo(10.71f, 18.71f)
            lineTo(9.29f, 17.29f)
            lineTo(14.59f, 12f)
            close()
        }
    }

    // 22. Check
    val Check: ImageVector by lazy {
        buildIcon("Check") {
            moveTo(9f, 16.17f)
            lineTo(4.83f, 12f)
            lineTo(3.41f, 13.41f)
            lineTo(9f, 19f)
            lineTo(21f, 7f)
            lineTo(19.59f, 5.59f)
            close()
        }
    }

    // 23. Trash
    val Trash: ImageVector by lazy {
        buildIcon("Trash") {
            moveTo(6f, 19f)
            curveTo(6f, 20.1f, 6.9f, 21f, 8f, 21f)
            lineTo(16f, 21f)
            curveTo(17.1f, 21f, 18f, 20.1f, 18f, 19f)
            lineTo(18f, 7f)
            lineTo(6f, 7f)
            lineTo(6f, 19f)
            close()
            moveTo(19f, 4f)
            lineTo(15.5f, 4f)
            lineTo(14.5f, 3f)
            lineTo(9.5f, 3f)
            lineTo(8.5f, 4f)
            lineTo(5f, 4f)
            lineTo(5f, 6f)
            lineTo(19f, 6f)
            close()
        }
    }

    // 24. Solo
    val Solo: ImageVector by lazy {
        buildIcon("Solo") {
            moveTo(16f, 7.5f)
            curveTo(16f, 6.1f, 14.9f, 5f, 13.5f, 5f)
            lineTo(10.5f, 5f)
            curveTo(9.1f, 5f, 8f, 6.1f, 8f, 7.5f)
            curveTo(8f, 8.9f, 9.1f, 10f, 10.5f, 10f)
            lineTo(13.5f, 10f)
            curveTo(14.9f, 10f, 16f, 11.1f, 16f, 12.5f)
            curveTo(16f, 13.9f, 14.9f, 15f, 13.5f, 15f)
            lineTo(10.5f, 15f)
            curveTo(9.1f, 15f, 8f, 13.9f, 8f, 12.5f)
            lineTo(6f, 12.5f)
            curveTo(6f, 15f, 8f, 17f, 10.5f, 17f)
            lineTo(13.5f, 17f)
            curveTo(16f, 17f, 18f, 15f, 18f, 12.5f)
            curveTo(18f, 10.5f, 16.5f, 8.8f, 14.5f, 8.2f)
            curveTo(15.4f, 7.8f, 16f, 6.9f, 16f, 5.8f)
            close()
        }
    }

    // 25. Mute
    val Mute: ImageVector by lazy {
        buildIcon("Mute") {
            moveTo(3.63f, 3.63f)
            lineTo(2.22f, 5.04f)
            lineTo(7.18f, 10f)
            lineTo(7f, 10f)
            lineTo(4f, 10f)
            lineTo(4f, 14f)
            lineTo(7f, 14f)
            lineTo(12f, 19f)
            lineTo(12f, 14.82f)
            lineTo(16.27f, 19.09f)
            curveTo(15.62f, 19.59f, 14.87f, 19.95f, 14f, 20.12f)
            lineTo(14f, 22.18f)
            curveTo(15.41f, 21.95f, 16.69f, 21.33f, 17.76f, 20.46f)
            lineTo(18.96f, 21.66f)
            lineTo(20.37f, 20.25f)
            close()
            moveTo(12f, 5f)
            lineTo(9.91f, 7.09f)
            lineTo(12f, 9.18f)
            close()
        }
    }

    // 26. Slice
    val Slice: ImageVector by lazy {
        buildIcon("Slice") {
            moveTo(9.64f, 7.64f)
            curveTo(9.87f, 7.14f, 10f, 6.59f, 10f, 6f)
            curveTo(10f, 3.79f, 8.21f, 2f, 6f, 2f)
            curveTo(3.79f, 2f, 2f, 3.79f, 2f, 6f)
            curveTo(2f, 8.21f, 3.79f, 10f, 6f, 10f)
            curveTo(6.59f, 10f, 7.14f, 9.87f, 7.64f, 9.64f)
            lineTo(10f, 12f)
            lineTo(7.64f, 14.36f)
            curveTo(7.14f, 14.13f, 6.59f, 14f, 6f, 14f)
            curveTo(3.79f, 14f, 2f, 15.79f, 2f, 18f)
            curveTo(2f, 20.21f, 3.79f, 22f, 6f, 22f)
            curveTo(8.21f, 22f, 10f, 20.21f, 10f, 18f)
            curveTo(10f, 17.41f, 9.87f, 16.86f, 9.64f, 16.36f)
            lineTo(12f, 14f)
            lineTo(19f, 21f)
            lineTo(22f, 21f)
            lineTo(22f, 20f)
            lineTo(12f, 10f)
            close()
            moveTo(6f, 8f)
            curveTo(4.9f, 8f, 4f, 7.1f, 4f, 6f)
            curveTo(4f, 4.9f, 4.9f, 4f, 6f, 4f)
            curveTo(7.1f, 4f, 8f, 4.9f, 8f, 6f)
            curveTo(8f, 7.1f, 7.1f, 8f, 6f, 8f)
            close()
            moveTo(6f, 20f)
            curveTo(4.9f, 20f, 4f, 19.1f, 4f, 18f)
            curveTo(4f, 16.9f, 4.9f, 16f, 6f, 16f)
            curveTo(7.1f, 16f, 8f, 16.9f, 8f, 18f)
            curveTo(8f, 19.1f, 7.1f, 20f, 6f, 20f)
            close()
        }
    }

    // 27. UnlinkLoop
    val UnlinkLoop: ImageVector by lazy {
        buildIcon("UnlinkLoop") {
            moveTo(17f, 7f)
            lineTo(14f, 7f)
            lineTo(14f, 9f)
            lineTo(17f, 9f)
            curveTo(18.66f, 9f, 20f, 10.34f, 20f, 12f)
            curveTo(20f, 13.66f, 18.66f, 15f, 17f, 15f)
            lineTo(14f, 15f)
            lineTo(14f, 17f)
            lineTo(17f, 17f)
            curveTo(19.76f, 17f, 22f, 14.76f, 22f, 12f)
            curveTo(22f, 9.24f, 19.76f, 7f, 17f, 7f)
            close()
            moveTo(10f, 15f)
            lineTo(7f, 15f)
            curveTo(5.34f, 15f, 4f, 13.66f, 4f, 12f)
            curveTo(4f, 10.34f, 5.34f, 9f, 7f, 9f)
            lineTo(10f, 9f)
            lineTo(10f, 7f)
            lineTo(7f, 7f)
            curveTo(4.24f, 7f, 2f, 9.24f, 2f, 12f)
            curveTo(2f, 14.76f, 4.24f, 17f, 7f, 17f)
            lineTo(10f, 17f)
            close()
        }
    }

    // 28. Combine
    val Combine: ImageVector by lazy {
        buildIcon("Combine") {
            moveTo(8f, 4f)
            lineTo(8f, 10f)
            lineTo(14f, 10f)
            lineTo(14f, 4f)
            close()
            moveTo(10f, 14f)
            lineTo(10f, 20f)
            lineTo(16f, 20f)
            lineTo(16f, 14f)
            close()
            moveTo(2f, 12f)
            lineTo(6f, 9f)
            lineTo(6f, 11f)
            lineTo(18f, 11f)
            lineTo(18f, 9f)
            lineTo(22f, 12f)
            lineTo(18f, 15f)
            lineTo(18f, 13f)
            lineTo(6f, 13f)
            lineTo(6f, 15f)
            close()
        }
    }

    // 29. Copy
    val Copy: ImageVector by lazy {
        buildIcon("Copy") {
            moveTo(16f, 1f)
            lineTo(4f, 1f)
            curveTo(2.9f, 1f, 2f, 1.9f, 2f, 3f)
            lineTo(2f, 17f)
            lineTo(4f, 17f)
            lineTo(4f, 3f)
            lineTo(16f, 3f)
            close()
            moveTo(19f, 5f)
            lineTo(8f, 5f)
            curveTo(6.9f, 5f, 6f, 5.9f, 6f, 7f)
            lineTo(6f, 21f)
            curveTo(6f, 22.1f, 6.9f, 23f, 8f, 23f)
            lineTo(19f, 23f)
            curveTo(20.1f, 23f, 21f, 22.1f, 21f, 21f)
            lineTo(21f, 7f)
            curveTo(21f, 5.9f, 20.1f, 5f, 19f, 5f)
            close()
            moveTo(19f, 21f)
            lineTo(8f, 21f)
            lineTo(8f, 7f)
            lineTo(19f, 7f)
            close()
        }
    }

    // 30. Edit
    val Edit: ImageVector by lazy {
        buildIcon("Edit") {
            moveTo(3f, 17.25f)
            lineTo(3f, 21f)
            lineTo(6.75f, 21f)
            lineTo(17.81f, 9.94f)
            lineTo(14.06f, 6.19f)
            close()
            moveTo(20.71f, 7.04f)
            curveTo(21.1f, 6.65f, 21.1f, 6.02f, 20.71f, 5.63f)
            lineTo(18.37f, 3.29f)
            curveTo(17.98f, 2.9f, 17.35f, 2.9f, 16.96f, 3.29f)
            lineTo(15.13f, 5.12f)
            lineTo(18.88f, 8.87f)
            close()
        }
    }

    // 31. Snap
    val Snap: ImageVector by lazy {
        buildIcon("Snap") {
            moveTo(4f, 4f)
            lineTo(4f, 11f)
            curveTo(4f, 15.42f, 7.58f, 19f, 12f, 19f)
            curveTo(16.42f, 19f, 20f, 15.42f, 20f, 11f)
            lineTo(20f, 4f)
            lineTo(16f, 4f)
            lineTo(16f, 11f)
            curveTo(16f, 13.21f, 14.21f, 15f, 12f, 15f)
            curveTo(9.79f, 15f, 8f, 13.21f, 8f, 11f)
            lineTo(8f, 4f)
            close()
        }
    }

    // 32. More
    val More: ImageVector by lazy {
        buildIcon("More") {
            moveTo(6f, 10f)
            curveTo(4.9f, 10f, 4f, 10.9f, 4f, 12f)
            curveTo(4f, 13.1f, 4.9f, 14f, 6f, 14f)
            curveTo(7.1f, 14f, 8f, 13.1f, 8f, 12f)
            curveTo(8f, 10.9f, 7.1f, 10f, 6f, 10f)
            close()
            moveTo(12f, 10f)
            curveTo(10.9f, 10f, 10f, 10.9f, 10f, 12f)
            curveTo(10f, 13.1f, 10.9f, 14f, 12f, 14f)
            curveTo(13.1f, 14f, 14f, 13.1f, 14f, 12f)
            curveTo(14f, 10.9f, 13.1f, 10f, 12f, 10f)
            close()
            moveTo(18f, 10f)
            curveTo(16.9f, 10f, 16f, 10.9f, 16f, 12f)
            curveTo(16f, 13.1f, 16.9f, 14f, 18f, 14f)
            curveTo(19.1f, 14f, 20f, 13.1f, 20f, 12f)
            curveTo(20f, 10.9f, 19.1f, 10f, 18f, 10f)
            close()
        }
    }

    // 33. Metronome
    val Metronome: ImageVector by lazy {
        buildIcon("Metronome") {
            moveTo(12f, 1.75f)
            lineTo(6.25f, 20.25f)
            lineTo(17.75f, 20.25f)
            close()
            moveTo(12f, 5.5f)
            lineTo(15.5f, 18.25f)
            lineTo(8.5f, 18.25f)
            close()
            moveTo(11f, 10f)
            lineTo(17f, 6f)
            lineTo(18f, 7.5f)
            lineTo(12.5f, 11f)
            close()
        }
    }

    // 34. Tap
    val Tap: ImageVector by lazy {
        buildIcon("Tap") {
            moveTo(9f, 11.24f)
            lineTo(9f, 7.5f)
            curveTo(9f, 6.12f, 10.12f, 5f, 11.5f, 5f)
            curveTo(12.88f, 5f, 14f, 6.12f, 14f, 7.5f)
            lineTo(14f, 11.24f)
            curveTo(15.21f, 10.47f, 16f, 9.13f, 16f, 7.6f)
            curveTo(16f, 5.06f, 13.94f, 3f, 11.4f, 3f)
            curveTo(8.86f, 3f, 6.8f, 5.06f, 6.8f, 7.6f)
            curveTo(6.8f, 9.13f, 7.59f, 10.47f, 8.8f, 11.24f)
            close()
            moveTo(12f, 13f)
            curveTo(8.69f, 13f, 6f, 15.69f, 6f, 19f)
            lineTo(18f, 19f)
            curveTo(18f, 15.69f, 15.31f, 13f, 12f, 13f)
            close()
        }
    }

    // 35. Cpu
    val Cpu: ImageVector by lazy {
        buildIcon("Cpu") {
            moveTo(9f, 3f)
            lineTo(7f, 3f)
            lineTo(7f, 5f)
            lineTo(5f, 5f)
            curveTo(3.9f, 5f, 3f, 5.9f, 3f, 7f)
            lineTo(3f, 9f)
            lineTo(1f, 9f)
            lineTo(1f, 11f)
            lineTo(3f, 11f)
            lineTo(3f, 13f)
            lineTo(1f, 13f)
            lineTo(1f, 15f)
            lineTo(3f, 15f)
            lineTo(3f, 17f)
            curveTo(3.9f, 17f, 5f, 17.9f, 5f, 19f)
            lineTo(7f, 19f)
            lineTo(7f, 21f)
            lineTo(9f, 21f)
            lineTo(9f, 19f)
            lineTo(11f, 19f)
            lineTo(11f, 21f)
            lineTo(13f, 21f)
            lineTo(13f, 19f)
            lineTo(15f, 19f)
            lineTo(15f, 21f)
            lineTo(17f, 21f)
            lineTo(17f, 19f)
            lineTo(19f, 19f)
            curveTo(20.1f, 19f, 21f, 18.1f, 21f, 17f)
            lineTo(21f, 15f)
            lineTo(23f, 15f)
            lineTo(23f, 13f)
            lineTo(21f, 13f)
            lineTo(21f, 11f)
            lineTo(23f, 11f)
            lineTo(23f, 9f)
            lineTo(21f, 9f)
            lineTo(21f, 7f)
            curveTo(21f, 5.9f, 20.1f, 5f, 19f, 5f)
            lineTo(17f, 5f)
            lineTo(17f, 3f)
            lineTo(15f, 3f)
            lineTo(15f, 5f)
            lineTo(13f, 5f)
            lineTo(13f, 3f)
            lineTo(11f, 3f)
            lineTo(11f, 5f)
            lineTo(9f, 5f)
            close()
            moveTo(17f, 17f)
            lineTo(7f, 17f)
            lineTo(7f, 7f)
            lineTo(17f, 7f)
            close()
        }
    }

    // 36. Ram
    val Ram: ImageVector by lazy {
        buildIcon("Ram") {
            moveTo(2f, 7f)
            lineTo(22f, 7f)
            lineTo(22f, 17f)
            lineTo(2f, 17f)
            close()
            moveTo(4f, 9f)
            lineTo(4f, 15f)
            lineTo(7f, 15f)
            lineTo(7f, 9f)
            close()
            moveTo(9f, 9f)
            lineTo(9f, 15f)
            lineTo(12f, 15f)
            lineTo(12f, 9f)
            close()
            moveTo(14f, 9f)
            lineTo(14f, 15f)
            lineTo(17f, 15f)
            lineTo(17f, 9f)
            close()
            moveTo(19f, 9f)
            lineTo(19f, 15f)
            lineTo(20f, 15f)
            lineTo(20f, 9f)
            close()
        }
    }

    // 37. Save
    val Save: ImageVector by lazy {
        buildIcon("Save") {
            moveTo(17f, 3f)
            lineTo(5f, 3f)
            curveTo(3.89f, 3f, 3f, 3.9f, 3f, 5f)
            lineTo(3f, 19f)
            curveTo(3f, 20.1f, 3.89f, 21f, 5f, 21f)
            lineTo(19f, 21f)
            curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
            lineTo(21f, 7f)
            close()
            moveTo(12f, 19f)
            curveTo(10.34f, 19f, 9f, 17.66f, 9f, 16f)
            curveTo(9f, 14.34f, 10.34f, 13f, 12f, 13f)
            curveTo(13.66f, 13f, 15f, 14.34f, 15f, 16f)
            curveTo(15f, 17.66f, 13.66f, 19f, 12f, 19f)
            close()
            moveTo(15f, 9f)
            lineTo(5f, 9f)
            lineTo(5f, 5f)
            lineTo(15f, 5f)
            close()
        }
    }

    // 38. Folder
    val Folder: ImageVector by lazy {
        buildIcon("Folder") {
            moveTo(10f, 4f)
            lineTo(4f, 4f)
            curveTo(2.9f, 4f, 2.01f, 4.9f, 2.01f, 6f)
            lineTo(2f, 18f)
            curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f)
            lineTo(20f, 20f)
            curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
            lineTo(22f, 8f)
            curveTo(22f, 6.9f, 21.1f, 6f, 20f, 6f)
            lineTo(12f, 6f)
            close()
        }
    }

    // 39. Search
    val Search: ImageVector by lazy {
        buildIcon("Search") {
            moveTo(15.5f, 14f)
            lineTo(14.71f, 14f)
            lineTo(14.43f, 13.73f)
            curveTo(15.41f, 12.59f, 16f, 11.11f, 16f, 9.5f)
            curveTo(16f, 5.91f, 13.09f, 3f, 9.5f, 3f)
            curveTo(5.91f, 3f, 3f, 5.91f, 3f, 9.5f)
            curveTo(3f, 13.09f, 5.91f, 16f, 9.5f, 16f)
            curveTo(11.11f, 16f, 12.59f, 15.41f, 13.73f, 14.43f)
            lineTo(14f, 14.71f)
            lineTo(14f, 15.5f)
            lineTo(19f, 20.49f)
            lineTo(20.49f, 19f)
            close()
            moveTo(9.5f, 14f)
            curveTo(7.01f, 14f, 5f, 11.99f, 5f, 9.5f)
            curveTo(5f, 7.01f, 7.01f, 5f, 9.5f, 5f)
            curveTo(11.99f, 5f, 14f, 7.01f, 14f, 9.5f)
            curveTo(14f, 11.99f, 11.99f, 14f, 9.5f, 14f)
            close()
        }
    }

    // 40. Sort
    val Sort: ImageVector by lazy {
        buildIcon("Sort") {
            moveTo(3f, 18f)
            lineTo(9f, 18f)
            lineTo(9f, 16f)
            lineTo(3f, 16f)
            close()
            moveTo(3f, 6f)
            lineTo(3f, 8f)
            lineTo(21f, 8f)
            lineTo(21f, 6f)
            close()
            moveTo(3f, 13f)
            lineTo(15f, 13f)
            lineTo(15f, 11f)
            lineTo(3f, 11f)
            close()
        }
    }
}


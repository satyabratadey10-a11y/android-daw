package com.android.daw.ui.theme

/**
 * SD Studio DAW Z-Axis Elevation Index
 *
 * Strict single source of truth for Modifier.zIndex():
 * Layer.Workspace (0f) -> Grid, lanes, empty timeline space
 * Layer.Content   (1f) -> Clips, notes, mixer strips
 * Layer.Playhead  (2f) -> Playhead line, selection rectangles, ruler markers
 * Layer.Floating  (3f) -> Circular floating buttons, add-track FAB, action chips
 * Layer.Panel     (4f) -> Side drawers, module drawer, instrument panel
 * Layer.Popup     (5f) -> Context menus, BPM dial
 * Layer.Modal     (6f) -> Scrim + dialogs
 * Layer.Toast     (7f) -> Transient overlay messages
 */
object Layer {
    const val Workspace = 0f
    const val Content   = 1f
    const val Playhead  = 2f
    const val Floating  = 3f
    const val Panel     = 4f
    const val Popup     = 5f
    const val Modal     = 6f
    const val Toast     = 7f
}

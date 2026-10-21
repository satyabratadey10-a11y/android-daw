package com.android.daw.viewmodel

/**
 * UndoRedoManager
 *
 * Implements a bounded 200-step immutable snapshot history stack for non-destructive
 * timeline, track, and piano roll note editing operations per agy_ui_overhaul_prompt.md Section 5.
 */
class UndoRedoManager(
    private val maxHistorySize: Int = 200
) {
    private val undoStack = ArrayDeque<List<TrackUiModel>>()
    private val redoStack = ArrayDeque<List<TrackUiModel>>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /**
     * Records a new state mutation. Clears the redo stack.
     */
    fun pushState(tracks: List<TrackUiModel>) {
        if (undoStack.size >= maxHistorySize) {
            undoStack.removeFirst()
        }
        undoStack.addLast(tracks)
        redoStack.clear()
    }

    /**
     * Reverts to the previous snapshot, saving current state onto redo stack.
     */
    fun undo(currentTracks: List<TrackUiModel>): List<TrackUiModel>? {
        if (undoStack.isEmpty()) return null
        val previousState = undoStack.removeLast()
        if (redoStack.size >= maxHistorySize) {
            redoStack.removeFirst()
        }
        redoStack.addLast(currentTracks)
        return previousState
    }

    /**
     * Re-applies the next snapshot, saving current state onto undo stack.
     */
    fun redo(currentTracks: List<TrackUiModel>): List<TrackUiModel>? {
        if (redoStack.isEmpty()) return null
        val nextState = redoStack.removeLast()
        if (undoStack.size >= maxHistorySize) {
            undoStack.removeFirst()
        }
        undoStack.addLast(currentTracks)
        return nextState
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}

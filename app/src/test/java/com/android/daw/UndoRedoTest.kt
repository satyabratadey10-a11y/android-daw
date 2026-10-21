package com.android.daw

import com.android.daw.viewmodel.AudioClipUiModel
import com.android.daw.viewmodel.MidiNote
import com.android.daw.viewmodel.TrackInstrumentType
import com.android.daw.viewmodel.TrackUiModel
import com.android.daw.viewmodel.UndoRedoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying [UndoRedoManager]:
 * - Bounded 200-step immutable snapshot history
 * - Push, Undo, Redo state transitions
 * - Redo stack invalidation on new mutation
 * - 200-step bounded eviction
 */
class UndoRedoTest {

    private lateinit var undoRedoManager: UndoRedoManager

    @Before
    fun setUp() {
        undoRedoManager = UndoRedoManager(maxHistorySize = 200)
    }

    @Test
    fun testInitialStateIsEmpty() {
        assertFalse(undoRedoManager.canUndo)
        assertFalse(undoRedoManager.canRedo)
        assertNull(undoRedoManager.undo(emptyList()))
        assertNull(undoRedoManager.redo(emptyList()))
    }

    @Test
    fun testPushAndUndoSingleState() {
        val state0 = emptyList<TrackUiModel>()
        val state1 = listOf(
            TrackUiModel(trackId = 1, name = "Sub Synth 1", instrumentType = TrackInstrumentType.SUB_SYNTH)
        )

        undoRedoManager.pushState(state0)
        assertTrue(undoRedoManager.canUndo)
        assertFalse(undoRedoManager.canRedo)

        val restored = undoRedoManager.undo(state1)
        assertNotNull(restored)
        assertEquals(state0, restored)
        assertFalse(undoRedoManager.canUndo)
        assertTrue(undoRedoManager.canRedo)
    }

    @Test
    fun testRedoReappliesState() {
        val state0 = emptyList<TrackUiModel>()
        val state1 = listOf(
            TrackUiModel(trackId = 1, name = "Drum Kit 1", instrumentType = TrackInstrumentType.DRUM_KIT)
        )

        undoRedoManager.pushState(state0)
        val afterUndo = undoRedoManager.undo(state1)
        assertEquals(state0, afterUndo)

        val afterRedo = undoRedoManager.redo(state0)
        assertEquals(state1, afterRedo)
        assertTrue(undoRedoManager.canUndo)
        assertFalse(undoRedoManager.canRedo)
    }

    @Test
    fun testRedoStackClearedOnNewPush() {
        val state0 = emptyList<TrackUiModel>()
        val state1 = listOf(TrackUiModel(trackId = 1, name = "Track 1"))
        val state2 = listOf(TrackUiModel(trackId = 1, name = "Track 1 Modified"))

        undoRedoManager.pushState(state0)
        undoRedoManager.undo(state1)
        assertTrue(undoRedoManager.canRedo)

        // New mutation clears redo
        undoRedoManager.pushState(state2)
        assertFalse(undoRedoManager.canRedo)
        assertTrue(undoRedoManager.canUndo)
    }

    @Test
    fun testBounded200StepEviction() {
        val smallManager = UndoRedoManager(maxHistorySize = 5)

        for (i in 0 until 10) {
            smallManager.pushState(
                listOf(TrackUiModel(trackId = i, name = "Track $i"))
            )
        }

        // Can undo at most 5 times (maxHistorySize = 5)
        var undoCount = 0
        var current = listOf(TrackUiModel(trackId = 99, name = "Current"))
        while (smallManager.canUndo) {
            val prev = smallManager.undo(current)
            if (prev != null) {
                current = prev
                undoCount++
            } else {
                break
            }
        }

        assertEquals(5, undoCount)
        assertFalse(smallManager.canUndo)
        assertTrue(smallManager.canRedo)
    }

    @Test
    fun testMidiNoteStateUndoRedo() {
        val note1 = MidiNote(id = 1, pitch = 60, startFrame = 0L, durationFrames = 22050L, velocity = 0.8f)
        val note2 = MidiNote(id = 2, pitch = 64, startFrame = 22050L, durationFrames = 22050L, velocity = 0.9f)

        val clipWithoutNotes = AudioClipUiModel(clipId = 10, trackId = 1, name = "Pattern", startFrame = 0L, durationFrames = 44100L, isMidi = true, notes = emptyList())
        val clipWithNote1 = clipWithoutNotes.copy(notes = listOf(note1))
        val clipWithNotes1And2 = clipWithoutNotes.copy(notes = listOf(note1, note2))

        val state0 = listOf(TrackUiModel(trackId = 1, name = "Synth", clips = listOf(clipWithoutNotes)))
        val state1 = listOf(TrackUiModel(trackId = 1, name = "Synth", clips = listOf(clipWithNote1)))
        val state2 = listOf(TrackUiModel(trackId = 1, name = "Synth", clips = listOf(clipWithNotes1And2)))

        undoRedoManager.pushState(state0)
        undoRedoManager.pushState(state1)

        val undoStep1 = undoRedoManager.undo(state2)
        assertEquals(1, undoStep1?.first()?.clips?.first()?.notes?.size)
        assertEquals(60, undoStep1?.first()?.clips?.first()?.notes?.first()?.pitch)

        val undoStep2 = undoRedoManager.undo(undoStep1!!)
        assertEquals(0, undoStep2?.first()?.clips?.first()?.notes?.size)

        val redoStep1 = undoRedoManager.redo(undoStep2!!)
        assertEquals(1, redoStep1?.first()?.clips?.first()?.notes?.size)
    }
}

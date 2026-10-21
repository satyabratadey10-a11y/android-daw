package com.android.daw.ui.studio

import com.android.daw.viewmodel.PianoRollSnap

/**
 * PianoRollSnapMath
 *
 * Implements sample-accurate musical grid quantization math across snap resolutions:
 * OFF, 1/4, 1/8, 1/16, 1/32.
 */
object PianoRollSnapMath {

    fun quantizeFrame(frame: Long, snap: PianoRollSnap, bpm: Double, sampleRate: Int): Long {
        if (snap == PianoRollSnap.OFF || snap.fractionOfBeat <= 0.0) return maxOf(0L, frame)
        val framesPerBeat = (60.0 / bpm) * sampleRate
        val snapFrames = (framesPerBeat * snap.fractionOfBeat).toLong().coerceAtLeast(1L)
        val snapped = ((frame + (snapFrames / 2)) / snapFrames) * snapFrames
        return maxOf(0L, snapped)
    }

    fun quantizeDuration(durationFrames: Long, snap: PianoRollSnap, bpm: Double, sampleRate: Int): Long {
        if (snap == PianoRollSnap.OFF || snap.fractionOfBeat <= 0.0) return maxOf(256L, durationFrames)
        val framesPerBeat = (60.0 / bpm) * sampleRate
        val snapFrames = (framesPerBeat * snap.fractionOfBeat).toLong().coerceAtLeast(1L)
        val snapped = maxOf(1L, (durationFrames + (snapFrames / 2)) / snapFrames) * snapFrames
        return snapped
    }

    fun defaultNoteDuration(snap: PianoRollSnap, bpm: Double, sampleRate: Int): Long {
        val framesPerBeat = (60.0 / bpm) * sampleRate
        val fraction = if (snap == PianoRollSnap.OFF || snap.fractionOfBeat <= 0.0) 0.25 else snap.fractionOfBeat
        return (framesPerBeat * fraction).toLong().coerceAtLeast(256L)
    }

    fun framesPerSnap(snap: PianoRollSnap, bpm: Double, sampleRate: Int): Long {
        val framesPerBeat = (60.0 / bpm) * sampleRate
        val fraction = if (snap == PianoRollSnap.OFF || snap.fractionOfBeat <= 0.0) 0.25 else snap.fractionOfBeat
        return (framesPerBeat * fraction).toLong().coerceAtLeast(1L)
    }
}

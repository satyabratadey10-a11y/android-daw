package com.android.daw

import com.android.daw.domain.model.AudioClipModel
import com.android.daw.domain.model.BbtTimecode
import com.android.daw.domain.model.DelayModel
import com.android.daw.domain.model.EqFilterType
import com.android.daw.domain.model.MasterLimiterModel
import com.android.daw.domain.model.StereoLevel
import com.android.daw.domain.model.TelemetryData
import com.android.daw.domain.model.TimeSignature
import com.android.daw.domain.model.TrackUiModel
import com.android.daw.domain.model.TransportState
import com.android.daw.domain.model.WaveformPyramid
import com.android.daw.domain.model.defaultEqBands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Unit test suite for domain audio models, DSP parameter conversions,
 * immutability invariants, and telemetry snapshotting.
 */
class AudioModelsTest {

    @Test
    fun testStereoLevelLinearToDbConversions() {
        // Digital Full Scale (1.0) = 0.0 dBFS
        assertEquals(0.0f, StereoLevel.linearToDb(1.0f), 1e-4f)

        // Half amplitude (0.5) ~ -6.0206 dB
        val minus6Db = StereoLevel.linearToDb(0.5f)
        assertTrue("0.5 linear should be ~-6.02 dB, got $minus6Db", abs(minus6Db - (-6.0206f)) < 0.05f)

        // Double amplitude (2.0) ~ +6.0206 dB
        val plus6Db = StereoLevel.linearToDb(2.0f)
        assertTrue("2.0 linear should be ~+6.02 dB, got $plus6Db", abs(plus6Db - 6.0206f) < 0.05f)

        // Tenth amplitude (0.1) = -20 dB
        assertEquals(-20.0f, StereoLevel.linearToDb(0.1f), 0.05f)

        // Digital silence clamped to MIN_DB (-60.0 dB)
        assertEquals(StereoLevel.MIN_DB, StereoLevel.linearToDb(0.0f), 1e-6f)
        assertEquals(StereoLevel.MIN_DB, StereoLevel.linearToDb(-1.0f), 1e-6f)
        assertEquals(StereoLevel.MIN_DB, StereoLevel.linearToDb(0.0000001f), 1e-6f)
    }

    @Test
    fun testStereoLevelPropertyGetters() {
        val level = StereoLevel(
            leftPeak = 1.0f,
            rightPeak = 0.5f,
            leftRms = 0.25f,
            rightRms = 0.0f
        )

        assertEquals(0.0f, level.leftPeakDb, 0.01f)
        assertTrue(level.rightPeakDb < -5.9f && level.rightPeakDb > -6.1f)
        assertTrue(level.leftRmsDb < -11.9f && level.leftRmsDb > -12.1f)
        assertEquals(StereoLevel.MIN_DB, level.rightRmsDb, 1e-6f)
    }

    @Test
    fun testEqFilterTypeFromId() {
        assertEquals(EqFilterType.LOW_SHELF, EqFilterType.fromId(0))
        assertEquals(EqFilterType.PEAKING, EqFilterType.fromId(1))
        assertEquals(EqFilterType.HIGH_SHELF, EqFilterType.fromId(2))
        assertEquals(EqFilterType.LOW_PASS, EqFilterType.fromId(3))
        assertEquals(EqFilterType.HIGH_PASS, EqFilterType.fromId(4))

        // Unknown ID falls back to PEAKING
        assertEquals(EqFilterType.PEAKING, EqFilterType.fromId(999))
        assertEquals(EqFilterType.PEAKING, EqFilterType.fromId(-1))
    }

    @Test
    fun testDefaultEqBandsStructure() {
        val bands = defaultEqBands()
        assertEquals(5, bands.size)

        // Check index ordering
        for (i in 0 until 5) {
            assertEquals(i, bands[i].bandIndex)
            assertFalse(bands[i].isBypassed)
            assertEquals(0f, bands[i].gainDb, 1e-6f)
        }

        // Check standard studio filter layout
        assertEquals(EqFilterType.LOW_SHELF, bands[0].filterType)
        assertEquals(80f, bands[0].frequencyHz, 1e-6f)

        assertEquals(EqFilterType.PEAKING, bands[1].filterType)
        assertEquals(250f, bands[1].frequencyHz, 1e-6f)

        assertEquals(EqFilterType.PEAKING, bands[2].filterType)
        assertEquals(1000f, bands[2].frequencyHz, 1e-6f)

        assertEquals(EqFilterType.PEAKING, bands[3].filterType)
        assertEquals(4000f, bands[3].frequencyHz, 1e-6f)

        assertEquals(EqFilterType.HIGH_SHELF, bands[4].filterType)
        assertEquals(12000f, bands[4].frequencyHz, 1e-6f)
    }

    @Test
    fun testWaveformPyramidZoomSelection() {
        val l0 = floatArrayOf(0.1f, 0.2f, 0.3f)
        val l1 = floatArrayOf(0.4f, 0.5f, 0.6f)
        val l2 = floatArrayOf(0.7f, 0.8f, 0.9f)
        val l3 = floatArrayOf(1.0f, 1.1f, 1.2f)
        val l4 = floatArrayOf(1.3f, 1.4f, 1.5f)

        val pyramidWithL0 = WaveformPyramid(l1, l2, l3, l4, l0)
        assertEquals(l0, pyramidWithL0.getLevelForZoom(1.0f))
        assertEquals(l0, pyramidWithL0.getLevelForZoom(2.0f))
        assertEquals(l1, pyramidWithL0.getLevelForZoom(4.0f))
        assertEquals(l1, pyramidWithL0.getLevelForZoom(8.0f))
        assertEquals(l2, pyramidWithL0.getLevelForZoom(16.0f))
        assertEquals(l2, pyramidWithL0.getLevelForZoom(32.0f))
        assertEquals(l3, pyramidWithL0.getLevelForZoom(64.0f))
        assertEquals(l3, pyramidWithL0.getLevelForZoom(128.0f))
        assertEquals(l4, pyramidWithL0.getLevelForZoom(256.0f))

        // When level0 is empty, zoom <= 2 falls back to level1
        val pyramidNoL0 = WaveformPyramid(l1, l2, l3, l4)
        assertEquals(l1, pyramidNoL0.getLevelForZoom(1.0f))
    }

    @Test
    fun testWaveformPyramidEqualsAndHashCode() {
        val p1 = WaveformPyramid(floatArrayOf(1f), floatArrayOf(2f), floatArrayOf(3f), floatArrayOf(4f))
        val p2 = WaveformPyramid(floatArrayOf(1f), floatArrayOf(2f), floatArrayOf(3f), floatArrayOf(4f))
        val p3 = WaveformPyramid(floatArrayOf(9f), floatArrayOf(2f), floatArrayOf(3f), floatArrayOf(4f))

        assertEquals(p1, p2)
        assertEquals(p1.hashCode(), p2.hashCode())
        assertNotEquals(p1, p3)
    }

    @Test
    fun testTelemetryDataSnapshotIsolation() {
        val telemetry = TelemetryData()
        telemetry.playheadFrame = 44100L
        telemetry.masterPeakLeft = 0.9f
        telemetry.trackPeaks[0] = 0.5f
        telemetry.isPlaying = true

        val snapshot1 = telemetry.toSnapshot()
        assertEquals(44100L, snapshot1.playheadFrame)
        assertEquals(0.9f, snapshot1.masterPeakLeft, 1e-6f)
        assertEquals(0.5f, snapshot1.trackPeaks[0], 1e-6f)
        assertTrue(snapshot1.isPlaying)

        // Mutate original telemetry data: snapshot must NOT change
        telemetry.playheadFrame = 88200L
        telemetry.trackPeaks[0] = 0.99f
        telemetry.isPlaying = false

        assertEquals(44100L, snapshot1.playheadFrame)
        assertEquals(0.5f, snapshot1.trackPeaks[0], 1e-6f)
        assertTrue(snapshot1.isPlaying)

        val snapshot2 = telemetry.toSnapshot()
        assertEquals(88200L, snapshot2.playheadFrame)
        assertEquals(0.99f, snapshot2.trackPeaks[0], 1e-6f)
        assertFalse(snapshot2.isPlaying)
        assertNotEquals(snapshot1, snapshot2)
    }

    @Test
    fun testTrackUiModelCopySemantics() {
        val track = TrackUiModel(
            id = 1,
            name = "Audio Track 1",
            volume = 1.0f,
            pan = 0.0f
        )

        val muted = track.copy(isMuted = true)
        assertTrue(muted.isMuted)
        assertFalse(track.isMuted)

        val panned = track.copy(pan = -0.5f)
        assertEquals(-0.5f, panned.pan, 1e-6f)
        assertEquals(0.0f, track.pan, 1e-6f)
    }

    @Test
    fun testDelayAndLimiterModels() {
        val delay = DelayModel(delayMs = 300f, feedback = 0.4f, wetDry = 0.25f, isBypassed = false)
        assertEquals(300f, delay.delayMs, 1e-6f)
        assertEquals(0.4f, delay.feedback, 1e-6f)
        assertEquals(0.25f, delay.wetDry, 1e-6f)
        assertFalse(delay.isBypassed)

        val limiter = MasterLimiterModel(thresholdDb = -1.0f, ceilingDb = -0.2f, releaseMs = 60f)
        assertEquals(-1.0f, limiter.thresholdDb, 1e-6f)
        assertEquals(-0.2f, limiter.ceilingDb, 1e-6f)
        assertEquals(60f, limiter.releaseMs, 1e-6f)
        assertFalse(limiter.isBypassed)
    }

    @Test
    fun testTimeSignatureAndBbt() {
        val sig = TimeSignature(numerator = 3, denominator = 4)
        assertEquals(3, sig.numerator)
        assertEquals(4, sig.denominator)

        val bbt = BbtTimecode(bar = 4, beat = 2, tick = 480)
        assertEquals(4, bbt.bar)
        assertEquals(2, bbt.beat)
        assertEquals(480, bbt.tick)
    }
}

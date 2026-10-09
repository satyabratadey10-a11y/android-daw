package com.android.daw

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.android.daw.service.AudioFocusManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Unit test suite for [AudioFocusManager].
 * Verifies audio focus gain/loss state machine, transient auto-recovery,
 * becoming-noisy auto-pause broadcast receiver, and Bluetooth A2DP latency compensation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioFocusManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var mockContext: Context
    private lateinit var mockAudioManager: AudioManager

    private var pauseCount = 0
    private var resumeCount = 0

    private lateinit var manager: AudioFocusManager

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockAudioManager = mock(AudioManager::class.java)
        `when`(mockContext.getSystemService(Context.AUDIO_SERVICE)).thenReturn(mockAudioManager)

        pauseCount = 0
        resumeCount = 0

        manager = AudioFocusManager(
            context = mockContext,
            coroutineScope = testScope,
            onPausePlayback = { pauseCount++ },
            onResumePlayback = { resumeCount++ },
            dispatcher = testDispatcher
        )
    }

    @Test
    fun testTransientLossAndAutomaticRecovery() {
        val listener = getFocusChangeListener()

        // 1. Transient Loss occurs (e.g. phone call ringing)
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        testScope.advanceUntilIdle()
        assertEquals(1, pauseCount)
        assertEquals(0, resumeCount)

        // 2. Focus regained -> should automatically resume
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        testScope.advanceUntilIdle()
        assertEquals(1, pauseCount)
        assertEquals(1, resumeCount)
    }

    @Test
    fun testPermanentLossDoesNotAutoResume() {
        val listener = getFocusChangeListener()

        // 1. Permanent Loss occurs (another app started playback)
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        testScope.advanceUntilIdle()
        assertEquals(1, pauseCount)
        assertEquals(0, resumeCount)

        // 2. Focus regained -> should NOT auto-resume because loss was permanent
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        testScope.advanceUntilIdle()
        assertEquals(1, pauseCount)
        assertEquals(0, resumeCount)
    }

    @Test
    fun testTransientCanDuckPausesInProfessionalDaw() {
        val listener = getFocusChangeListener()

        // DAW pauses during ducking to preserve critical acoustic mix balance
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        testScope.advanceUntilIdle()
        assertEquals(1, pauseCount)
        assertEquals(0, resumeCount)

        // Focus restored
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        testScope.advanceUntilIdle()
        assertEquals(1, pauseCount)
        assertEquals(1, resumeCount)
    }

    @Test
    fun testBecomingNoisyReceiverTriggersAutoPause() {
        val captor = ArgumentCaptor.forClass(BroadcastReceiver::class.java)

        // Manually trigger receiver registration or simulate requestAudioFocus
        val regMethod = AudioFocusManager::class.java.getDeclaredMethod("registerBecomingNoisyReceiver")
        regMethod.isAccessible = true
        regMethod.invoke(manager)

        verify(mockContext).registerReceiver(captor.capture(), any(IntentFilter::class.java))
        val receiver = captor.value

        // Simulate ACTION_AUDIO_BECOMING_NOISY intent (unplugged headphones)
        val noisyIntent = mock(Intent::class.java)
        `when`(noisyIntent.action).thenReturn(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        receiver.onReceive(mockContext, noisyIntent)
        testScope.advanceUntilIdle()

        assertEquals(1, pauseCount)
    }

    @Test
    fun testBluetoothLatencyCompensationCalculations() {
        // When not Bluetooth active
        `when`(mockAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)).thenReturn(emptyArray())
        assertFalse(manager.isBluetoothA2dpActive())
        assertEquals(0L, manager.getBluetoothLatencyCompensationFrames(48000, 180f))

        // When Bluetooth A2DP device is connected
        val mockBtDevice = mock(AudioDeviceInfo::class.java)
        `when`(mockBtDevice.type).thenReturn(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        `when`(mockAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)).thenReturn(arrayOf(mockBtDevice))

        assertTrue(manager.isBluetoothA2dpActive())

        // 180 ms at 48000 Hz = 0.180 * 48000 = 8640 frames
        val compensation48k = manager.getBluetoothLatencyCompensationFrames(48000, 180f)
        assertEquals(8640L, compensation48k)

        // 200 ms at 44100 Hz = 0.200 * 44100 = 8820 frames
        val compensation44k = manager.getBluetoothLatencyCompensationFrames(44100, 200f)
        assertEquals(8820L, compensation44k)

        // Invalid sample rate guard
        assertEquals(0L, manager.getBluetoothLatencyCompensationFrames(0, 180f))
        assertEquals(0L, manager.getBluetoothLatencyCompensationFrames(-100, 180f))
    }

    private fun getFocusChangeListener(): AudioManager.OnAudioFocusChangeListener {
        val field = AudioFocusManager::class.java.getDeclaredField("focusChangeListener")
        field.isAccessible = true
        return field.get(manager) as AudioManager.OnAudioFocusChangeListener
    }
}

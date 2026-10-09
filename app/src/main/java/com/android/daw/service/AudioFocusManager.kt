package com.android.daw.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages audio focus acquisition, loss, recovery, and audio routing safeguards.
 *
 * Implements:
 * 1. [AudioFocusRequestCompat] handling permanent, transient, and ducking focus changes.
 * 2. [ACTION_AUDIO_BECOMING_NOISY] broadcast receiver for immediate auto-pause on headphone/BT disconnect.
 * 3. Bluetooth A2DP latency compensation calculation for overdub recording synchronization.
 */
class AudioFocusManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val onPausePlayback: () -> Unit,
    private val onResumePlayback: () -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var focusRequest: AudioFocusRequestCompat? = null
    private var resumeOnFocusGain: Boolean = false

    // Broadcast receiver for headphone disconnect
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                coroutineScope.launch(dispatcher) {
                    onPausePlayback()
                }
            }
        }
    }

    private var isNoisyReceiverRegistered: Boolean = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        coroutineScope.launch(dispatcher) {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    if (resumeOnFocusGain) {
                        resumeOnFocusGain = false
                        onResumePlayback()
                    }
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    resumeOnFocusGain = false
                    onPausePlayback()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    resumeOnFocusGain = true
                    onPausePlayback()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    // In a professional DAW, ducking alters mixing acoustics.
                    // We pause rather than attenuate, preserving acoustic balance.
                    resumeOnFocusGain = true
                    onPausePlayback()
                }
            }
        }
    }

    /**
     * Requests audio focus for media playback and registers the becoming-noisy receiver.
     *
     * @return True if focus was granted, false otherwise.
     */
    fun requestAudioFocus(): Boolean {
        val playbackAttributes = AudioAttributesCompat.Builder()
            .setUsage(AudioAttributesCompat.USAGE_MEDIA)
            .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
            .build()

        val request = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
            .setAudioAttributes(playbackAttributes)
            .setOnAudioFocusChangeListener(focusChangeListener)
            .build()

        focusRequest = request
        val result = AudioManagerCompat.requestAudioFocus(audioManager, request)
        val granted = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)

        if (granted) {
            registerBecomingNoisyReceiver()
        }

        return granted
    }

    /**
     * Abandons audio focus and unregisters the becoming-noisy receiver.
     */
    fun abandonAudioFocus() {
        focusRequest?.let {
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, it)
            focusRequest = null
            resumeOnFocusGain = false
        }
        unregisterBecomingNoisyReceiver()
    }

    /**
     * Registers receiver to automatically pause audio when wired or Bluetooth headphones disconnect.
     */
    private fun registerBecomingNoisyReceiver() {
        if (!isNoisyReceiverRegistered) {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            try {
                context.registerReceiver(becomingNoisyReceiver, filter)
                isNoisyReceiverRegistered = true
            } catch (_: Exception) {
                // Ignore registration errors
            }
        }
    }

    /**
     * Unregisters the becoming-noisy receiver.
     */
    private fun unregisterBecomingNoisyReceiver() {
        if (isNoisyReceiverRegistered) {
            try {
                context.unregisterReceiver(becomingNoisyReceiver)
            } catch (_: Exception) {
                // Ignore unregistration errors
            } finally {
                isNoisyReceiverRegistered = false
            }
        }
    }

    /**
     * Checks if the currently active audio output route is a Bluetooth A2DP device.
     */
    fun isBluetoothA2dpActive(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
    }

    /**
     * Calculates the Bluetooth latency compensation in frames to align microphone overdub recordings.
     *
     * @param sampleRate Audio engine sample rate (e.g. 44100 or 48000 Hz).
     * @param defaultCalibrationMs Default latency estimate in milliseconds (baseline 180 ms).
     */
    fun getBluetoothLatencyCompensationFrames(
        sampleRate: Int,
        defaultCalibrationMs: Float = 180f
    ): Long {
        if (!isBluetoothA2dpActive() || sampleRate <= 0) return 0L
        return ((defaultCalibrationMs / 1000f) * sampleRate).toLong()
    }
}

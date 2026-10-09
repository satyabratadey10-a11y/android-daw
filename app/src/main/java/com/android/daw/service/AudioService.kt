package com.android.daw.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.android.daw.bridge.NativeAudioEngine
import com.android.daw.domain.model.TransportState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground Audio Playback Service for the Android DAW.
 *
 * Manages playback lifecycle outside the Activity context, coordinates
 * [NativeAudioEngine], [AudioFocusManager], and [AudioTelemetryCoordinator],
 * and maintains persistent Media notifications with playback transport controls.
 */
class AudioService : Service() {

    companion object {
        const val CHANNEL_ID = "daw_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "com.android.daw.action.PLAY"
        const val ACTION_PAUSE = "com.android.daw.action.PAUSE"
        const val ACTION_STOP = "com.android.daw.action.STOP"
        const val ACTION_REWIND = "com.android.daw.action.REWIND"

        fun createPlayIntent(context: Context): Intent =
            Intent(context, AudioService::class.java).apply { action = ACTION_PLAY }

        fun createPauseIntent(context: Context): Intent =
            Intent(context, AudioService::class.java).apply { action = ACTION_PAUSE }

        fun createStopIntent(context: Context): Intent =
            Intent(context, AudioService::class.java).apply { action = ACTION_STOP }
    }

    private val binder = AudioServiceBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val audioEngine: NativeAudioEngine = NativeAudioEngine.getInstance()
    val telemetryCoordinator: AudioTelemetryCoordinator = AudioTelemetryCoordinator(audioEngine)

    private val _playbackState = MutableStateFlow(TransportState.STOPPED)
    val playbackState: StateFlow<TransportState> = _playbackState.asStateFlow()

    private lateinit var audioFocusManager: AudioFocusManager

    inner class AudioServiceBinder : Binder() {
        fun getService(): AudioService = this@AudioService
        fun getTelemetryCoordinator(): AudioTelemetryCoordinator = telemetryCoordinator
        fun getNativeAudioEngine(): NativeAudioEngine = audioEngine
        fun getAudioFocusManager(): AudioFocusManager = audioFocusManager
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        audioFocusManager = AudioFocusManager(
            context = applicationContext,
            coroutineScope = serviceScope,
            onPausePlayback = { pausePlayback() },
            onResumePlayback = { startPlayback() }
        )

        telemetryCoordinator.start(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> startPlayback()
            ACTION_PAUSE -> pausePlayback()
            ACTION_STOP -> stopPlayback()
            ACTION_REWIND -> rewindPlayback()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
        audioFocusManager.abandonAudioFocus()
        telemetryCoordinator.stop()
        serviceScope.cancel()
    }

    /**
     * Initiates audio playback, acquires audio focus, and elevates to foreground service.
     */
    fun startPlayback() {
        serviceScope.launch {
            val focusGranted = audioFocusManager.requestAudioFocus()
            if (focusGranted) {
                audioEngine.play()
                _playbackState.value = TransportState.PLAYING
                updateForegroundNotification(TransportState.PLAYING)
            }
        }
    }

    /**
     * Pauses audio playback and updates foreground notification.
     */
    fun pausePlayback() {
        serviceScope.launch {
            audioEngine.pause()
            _playbackState.value = TransportState.PAUSED
            updateForegroundNotification(TransportState.PAUSED)
        }
    }

    /**
     * Stops playback, releases audio focus, and demotes from foreground service.
     */
    fun stopPlayback() {
        serviceScope.launch {
            audioEngine.stopTransport()
            audioFocusManager.abandonAudioFocus()
            _playbackState.value = TransportState.STOPPED
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
    }

    /**
     * Rewinds playhead to the project beginning (frame 0).
     */
    fun rewindPlayback() {
        serviceScope.launch {
            audioEngine.seekTo(0L)
        }
    }

    private fun updateForegroundNotification(state: TransportState) {
        val notification = buildNotification(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(state: TransportState): Notification {
        val flag = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val rewindPendingIntent = PendingIntent.getService(
            this, 1, Intent(this, AudioService::class.java).apply { action = ACTION_REWIND }, flag
        )
        val stopPendingIntent = PendingIntent.getService(
            this, 2, Intent(this, AudioService::class.java).apply { action = ACTION_STOP }, flag
        )

        val isPlaying = (state == TransportState.PLAYING)
        val playPauseAction = if (isPlaying) {
            val pausePendingIntent = PendingIntent.getService(
                this, 3, Intent(this, AudioService::class.java).apply { action = ACTION_PAUSE }, flag
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                "Pause",
                pausePendingIntent
            ).build()
        } else {
            val playPendingIntent = PendingIntent.getService(
                this, 4, Intent(this, AudioService::class.java).apply { action = ACTION_PLAY }, flag
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                "Play",
                playPendingIntent
            ).build()
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Android DAW Studio")
            .setContentText(if (isPlaying) "Playing Project" else "Playback Paused")
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Rewind", rewindPendingIntent)
            .addAction(playPauseAction)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DAW Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "DAW background playback controls and status"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}

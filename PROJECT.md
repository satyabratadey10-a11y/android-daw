# Project: Android Digital Audio Workstation (Android DAW)

## Architecture
The application is structured into four primary layers following Clean Architecture and unidirectional data flow (MVI/UDF), with strict decoupling between the real-time audio thread, background processing coroutines, and the Jetpack Compose UI.

```
┌────────────────────────────────────────────────────────────────────────┐
│ Jetpack Compose UI (app/src/main/java/.../ui)                          │
│  - Timeline Sequencer, Waveform Canvas, Playhead, Mixer, DSP Rack      │
│  - Strict android-ui-ux-architect compliance (3-color, 48dp targets)   │
└───────────────────────────────────▲────────────────────────────────────┘
                                    │ Immutable UiState (StateFlow)
                                    │ User Actions (Sealed Interface)
┌───────────────────────────────────┴────────────────────────────────────┐
│ Presentation & Domain State (app/src/main/java/.../viewmodel, /domain) │
│  - StudioViewModel (MVI state coordinator)                             │
│  - Telemetry Poller (Dispatchers.Default, 60 Hz lock-free polling)     │
│  - AudioFocusManager & DeviceBroadcastReceiver                         │
└───────────────────────────────────▲────────────────────────────────────┘
                                    │ JNI Bridge (DirectByteBuffer, primitives)
                                    │ Zero main-thread blocking (Dispatchers.IO)
┌───────────────────────────────────┴────────────────────────────────────┐
│ Native C++ Audio Engine (app/src/main/cpp)                             │
│  - SpscRingBuffer: Lock-free, zero-allocation, 64-byte cache padded    │
│  - AudioEngine: Multi-track summing, planar float audio bus            │
│  - DSP Pipeline: Parametric EQ (DF2T), Panner, Delay, Master Limiter   │
│  - WavIO: 16/24-bit RIFF parser/writer, offline mixdown renderer       │
│  - AAudioStreamManager: Low-latency exclusive stream I/O               │
└───────────────────────────────────▲────────────────────────────────────┘
                                    │ Hardware DAC / ADC
┌───────────────────────────────────┴────────────────────────────────────┐
│ Android OS (AAudio / OpenSL ES, AudioRecord, AudioTrack)               │
└────────────────────────────────────────────────────────────────────────┘
```

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Gradle & NDK Build Setup | Root & app Kotlin DSL scripts, CMake externalNativeBuild, wrapper, manifest | M1 | Survey 2 / ORIGINAL_REQUEST |
| 2 | Lock-Free SPSC Ring Buffer | Zero heap allocation, non-blocking audio thread ring buffer with acquire/release atomics and 64-byte cache padding | M2 | R1 / Survey 1 |
| 3 | Multi-Track Planar Engine | Multi-track playback, voice clip intersection, planar float32 audio summing | M2 | R1 / Survey 1 |
| 4 | Real-Time DSP Pipeline | Linear parameter smoothing, constant-power panning law, 5-band biquad parametric EQ, stereo delay line, master lookahead limiter | M2 | R1 / Survey 1 |
| 5 | WAV File Parsing & Writing | 16-bit & 24-bit PCM RIFF parser/writer with TPDF dithering, non-destructive streaming | M2 | R4 / Survey 1 |
| 6 | Offline Mixdown Renderer | Fast-than-realtime project mixdown to stereo 16/24-bit WAV file | M2 | R4 / Survey 1 |
| 7 | Thread-Safe JNI Bridge | Dynamic RegisterNatives, cached IDs, DirectByteBuffer passing, lock-free telemetry | M3 | R2 / Survey 2 |
| 8 | AAudio Stream Lifecycle | Exclusive low-latency stream, double burst buffering, device disconnection recovery | M3 | R2 / Survey 2 |
| 9 | Audio Focus & System Handling | AudioFocusRequestCompat, becoming-noisy auto-pause, microphone permissions | M3 | R2 / Survey 2 |
| 10 | Background Telemetry Poller | 60Hz coroutine polling atomic C++ registers feeding StateFlow (Rule 8 compliant) | M3 | R2 / Survey 2 |
| 11 | Multi-Track Timeline & Waveforms | Canvas waveform with 5-level decimation pyramid, zoom/scroll, frame-accurate playhead | M4 | R3 / Survey 3 |
| 12 | Track Lane Controls & Console | Track header (mute, solo, arm), logarithmic volume faders, pan pots | M4 | R3 / Survey 3 |
| 13 | Master Mixer Console | Channel strips, Master fader, IEC 60268-10 PPM ballistics meters, clipping LEDs | M4 | R3 / Survey 3 |
| 14 | Global Transport Bar | Play, record, pause, stop, rewind, loop, tempo/BPM, dual timecode (BBT & ms) | M4 | R3 / Survey 3 |
| 15 | DSP Rack & EQ Visualizer | Cascaded biquad transfer function magnitude visualizer, Delay, Limiter modal | M4 | R3 / Survey 3 |
| 16 | android-ui-ux-architect Standard | Zero duplicate buttons, zero mock controls, 3-color palette, 48dp targets, Z-axis | M4 | SKILL.md / Spec |
| 17 | Live Microphone Recording | PCM recording directly from microphone into armed track clips via SPSC ring buffer | M5 | R4 / Survey 1, 2 |
| 18 | Audio File Import & Placement | Non-destructive WAV import, background decimation calculation, clip placement | M5 | R4 / Survey 1, 3 |
| 19 | Synthetic Test Fixtures | Automated synthetic audio fixtures (sine, sweep, impulses) for reproducible testing | M5 | ORIGINAL_REQUEST |
| 20 | Automated Unit Tests & CI | C++ native tests, Kotlin JVM unit tests, GitHub Actions CI workflow verification | M6 | ORIGINAL_REQUEST |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Project Infrastructure & Build Setup | Root Gradle, app Gradle, CMake integration, NDK config, AndroidManifest | none | DONE |
| M2 | Native C++ Audio Engine & DSP | SPSC ring buffers, multi-track planar summing, DSP effects, WAV parser/writer, CMake | M1 | DONE |
| M3 | JNI Bridge & Android Audio Lifecycle | JNI dynamic registration, AAudio stream manager, audio focus, 60Hz telemetry coroutine | M1, M2 | DONE |
| M4 | Jetpack Compose Studio UI | Timeline, Waveform decimation Canvas, Mixer console, Transport, DSP Rack, android-ui-ux-architect | M1, M3 | DONE |
| M5 | Recording, Import/Export & Audio Fixtures | Microphone live recording, WAV import/clip placement, offline mixdown export, fixtures | M2, M3, M4 | IN_PROGRESS |
| M6 | Test Suite & CI Packaging Verification | JVM unit tests, C++ engine tests, Git staging & GitHub Actions CI pipeline push | M1, M2, M3, M4, M5 | IN_PROGRESS |


## Interface Contracts

### Native C++ Audio Engine ↔ JNI Bridge
```cpp
// Lifecycle & Transport
void nativeInit(int sampleRate, int framesPerBurst);
void nativeRelease();
void nativePlay();
void nativePause();
void nativeStop();
void nativeSeek(int64_t framePosition);
void nativeSetLoop(bool enabled, int64_t startFrame, int64_t endFrame);
void nativeSetTempo(double bpm);

// Track Management
int nativeAddTrack(const char* name);
void nativeRemoveTrack(int trackId);
void nativeSetTrackVolume(int trackId, float linearGain);
void nativeSetTrackPan(int trackId, float panPosition);
void nativeSetTrackMute(int trackId, bool muted);
void nativeSetTrackSolo(int trackId, bool soloed);
void nativeSetTrackArmed(int trackId, bool armed);

// DSP Parameter Mutation (Thread-safe lock-free atomic parameter stores)
void nativeSetTrackEqBand(int trackId, int bandIndex, int filterType, float freqHz, float gainDb, float qFactor);
void nativeSetTrackDelay(int trackId, float delayMs, float feedback, float wetDry);
void nativeSetMasterLimiter(float thresholdDb, float ceilingDb, float releaseMs);

// Telemetry & Level Metering (Non-blocking reading into DirectByteBuffer)
int64_t nativeGetPlaybackPositionFrames();
void nativeGetMeteringData(jobject directFloatBuffer, int numTracks);

// File IO & Offline Mixdown
bool nativeLoadClip(int trackId, int clipId, const char* wavFilePath, int64_t startOffsetFrames);
bool nativeStartRecording(int trackId, const char* destinationWavPath);
void nativeStopRecording(int trackId);
bool nativeRenderMixdown(const char* outputWavPath, int bitDepth, int64_t totalFrames);
```

### Kotlin ViewModel ↔ Jetpack Compose UI (UDF / MVI)
```kotlin
// Immutable State
data class StudioUiState(
    val transportState: TransportState = TransportState.STOPPED,
    val currentFrame: Long = 0L,
    val totalFrames: Long = 0L,
    val bpm: Double = 120.0,
    val isLooping: Boolean = false,
    val loopStartFrame: Long = 0L,
    val loopEndFrame: Long = 0L,
    val tracks: List<TrackUiModel> = emptyList(),
    val masterLevels: StereoLevel = StereoLevel(0f, 0f, 0f, 0f),
    val selectedTrackId: Int? = null,
    val isMixerViewActive: Boolean = false,
    val isDspInspectorOpen: Boolean = false,
    val isExporting: Boolean = false
)

// Sealed Action Intent
sealed interface StudioAction {
    data object Play : StudioAction
    data object Pause : StudioAction
    data object Stop : StudioAction
    data class Seek(val frame: Long) : StudioAction
    data class SetTempo(val bpm: Double) : StudioAction
    data object ToggleLoop : StudioAction
    data class AddTrack(val name: String) : StudioAction
    data class RemoveTrack(val trackId: Int) : StudioAction
    data class SetTrackVolume(val trackId: Int, val volume: Float) : StudioAction
    data class SetTrackPan(val trackId: Int, val pan: Float) : StudioAction
    data class ToggleMute(val trackId: Int) : StudioAction
    data class ToggleSolo(val trackId: Int) : StudioAction
    data class ToggleArm(val trackId: Int) : StudioAction
    data class SelectTrack(val trackId: Int?) : StudioAction
    data class UpdateEq(val trackId: Int, val bandIndex: Int, val freq: Float, val gain: Float, val q: Float) : StudioAction
    data class UpdateDelay(val trackId: Int, val timeMs: Float, val feedback: Float, val wetDry: Float) : StudioAction
    data class UpdateLimiter(val thresholdDb: Float, val ceilingDb: Float, val releaseMs: Float) : StudioAction
    data object ToggleMixerView : StudioAction
    data object ToggleDspInspector : StudioAction
    data class ImportAudio(val trackId: Int, val uri: android.net.Uri) : StudioAction
    data class StartExport(val bitDepth: Int) : StudioAction
}
```

## Code Layout
```
/data/data/com.termux/files/home/android_daw/
├── build.gradle.kts                          # Root project build script
├── settings.gradle.kts                       # Root project settings
├── gradle.properties                         # JVM arguments & AndroidX flags
├── gradlew                                   # Gradle wrapper script
├── gradlew.bat                               # Gradle wrapper Windows batch
├── gradle/wrapper/
│   ├── gradle-wrapper.properties             # Gradle 8.4 wrapper distribution
│   └── gradle-wrapper.jar                    # Gradle wrapper bootstrap jar
├── .github/workflows/
│   └── build.yml                             # GitHub Actions CI build & test pipeline
├── app/
│   ├── build.gradle.kts                      # Application module build script
│   ├── src/main/
│   │   ├── AndroidManifest.xml               # App manifest, permissions, audio flags
│   │   ├── cpp/                              # Native C++ Audio Engine
│   │   │   ├── CMakeLists.txt                # CMake build configuration
│   │   │   ├── audio/
│   │   │   │   ├── SpscRingBuffer.h          # Lock-free SPSC ring buffer header
│   │   │   │   ├── SpscRingBuffer.cpp        # Lock-free SPSC implementation
│   │   │   │   ├── AudioEngine.h             # Core multi-track audio engine
│   │   │   │   ├── AudioEngine.cpp
│   │   │   │   ├── AudioTrack.h              # Track model & voice summing
│   │   │   │   ├── AudioTrack.cpp
│   │   │   │   ├── AudioClip.h               # Audio clip on timeline
│   │   │   │   ├── AudioClip.cpp
│   │   │   │   ├── WavFile.h                 # RIFF/WAVE 16/24-bit parser & writer
│   │   │   │   ├── WavFile.cpp
│   │   │   │   ├── MixdownRenderer.h         # Faster-than-realtime offline mixdown
│   │   │   │   └── MixdownRenderer.cpp
│   │   │   ├── dsp/
│   │   │   │   ├── ParameterSmoother.h       # Zipper-free parameter smoothing
│   │   │   │   ├── Panner.h                  # Constant-power panning law
│   │   │   │   ├── Panner.cpp
│   │   │   │   ├── BiquadFilter.h            # DF2T biquad filter (Audio EQ Cookbook)
│   │   │   │   ├── BiquadFilter.cpp
│   │   │   │   ├── ParametricEq.h            # 5-band parametric equalizer
│   │   │   │   ├── ParametricEq.cpp
│   │   │   │   ├── DelayEffect.h             # Stereo delay line with damping
│   │   │   │   ├── DelayEffect.cpp
│   │   │   │   ├── MasterLimiter.h           # Lookahead limiter & saturation
│   │   │   │   └── MasterLimiter.cpp
│   │   │   ├── backend/
│   │   │   │   ├── AAudioStreamManager.h     # AAudio low-latency stream I/O
│   │   │   │   └── AAudioStreamManager.cpp
│   │   │   └── jni/
│   │   │       ├── JniBridge.cpp             # Dynamic RegisterNatives JNI bridge
│   │   │       └── JniBridge.h
│   │   ├── java/com/android/daw/
│   │   │   ├── DawApplication.kt             # Application class
│   │   │   ├── MainActivity.kt               # Main Activity with edge-to-edge Compose
│   │   │   ├── bridge/
│   │   │   │   └── NativeAudioEngine.kt      # Kotlin JNI interface singleton
│   │   │   ├── service/
│   │   │   │   ├── AudioService.kt           # Foreground media service
│   │   │   │   ├── AudioFocusManager.kt      # Audio focus handler
│   │   │   │   └── AudioTelemetryCoordinator.kt # 60Hz background telemetry coroutine
│   │   │   ├── domain/
│   │   │   │   ├── model/AudioModels.kt      # Track, Clip, StereoLevel domain data
│   │   │   │   ├── decimation/WaveformDecimator.kt # Multi-resolution peak pyramid
│   │   │   │   └── timecode/TimecodeFormatter.kt   # BBT & SMPTE/ms calculators
│   │   │   ├── viewmodel/
│   │   │   │   ├── StudioViewModel.kt        # MVI ViewModel with StateFlow
│   │   │   │   ├── StudioUiState.kt          # Immutable UI state
│   │   │   │   └── StudioAction.kt           # Sealed UI action hierarchy
│   │   │   └── ui/
│   │   │       ├── theme/
│   │   │       │   ├── Color.kt              # 3-color palette (Rule 3)
│   │   │       │   ├── Theme.kt              # Dark studio theme
│   │   │       │   └── Type.kt               # High-contrast typography scale
│   │   │       ├── components/
│   │   │       │   ├── Fader.kt              # Logarithmic volume fader with dB readout
│   │   │       │   ├── PanPot.kt             # Center-detent stereo pan pot
│   │   │       │   ├── LevelMeter.kt         # IEC 60268-10 PPM ballistics level meter
│   │   │       │   ├── WaveformCanvas.kt     # High-performance Canvas waveform painter
│   │   │       │   └── EqCurveVisualizer.kt  # Composite biquad frequency visualizer
│   │   │       ├── studio/
│   │   │       │   ├── StudioScreen.kt       # Root Scaffold & thumb-zone layout
│   │   │       │   ├── TransportBar.kt       # Transport controls in bottom 40%
│   │   │       │   ├── TimelineView.kt       # Multi-track lanes & playhead
│   │   │       │   ├── TrackHeader.kt        # Track lane header (M, S, R buttons)
│   │   │       │   ├── MixerView.kt          # Master mixer console & channel strips
│   │   │       │   └── DspRackModal.kt       # DSP effects rack modal sheet
│   │   └── res/                              # Android resources (strings, icons, themes)
│   └── src/test/
│       ├── java/com/android/daw/             # JVM unit tests (ViewModel, math, timecode)
│       └── cpp/                              # C++ engine unit tests (SPSC, DSP, WAV)
```

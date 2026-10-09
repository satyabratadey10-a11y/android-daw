# E2E Test Infra: Android Digital Audio Workstation

## Test Philosophy
- Requirement-driven, opaque-box, and modular unit testing across both native C++ audio engine layers and Kotlin JVM/Robolectric presentation layers.
- Full automated test suite integrated directly with GitHub Actions CI pipeline (`.github/workflows/build.yml`).
- Verification follows Category-Partition + Boundary Value Analysis (BVA) + Pairwise Combinations + Real-World DAW Workload Testing.

## Feature Inventory & Test Mapping
| # | Feature Area | Tier 1 (Coverage) | Tier 2 (Boundary) | Tier 3 (Pairwise) | Tier 4 (Real-World) |
|---|--------------|:-----------------:|:-----------------:|:-----------------:|:-------------------:|
| F1 | Lock-Free SPSC Ring Buffer | 5 cases (push, pop, wrap, empty, full) | 5 cases (0-byte, single-byte, capacity-1, overflow, underflow) | Concurrency stress | Multi-thread RT audio pump |
| F2 | Planar Multi-Track Summing | 5 cases (mono, stereo, unity, mute, solo) | 5 cases (extreme gain, max tracks, silent tracks, DC offset) | Pan + Gain interactions | 16-track full mixdown |
| F3 | Stereo Panning Law | 5 cases (Hard L, Center, Hard R, Mid-L, Mid-R) | 5 cases (boundary values -1.0, 1.0, 0.0, outside [-1,1]) | Pan + Mute + Solo | Dynamic automated pan sweep |
| F4 | Parametric EQ (Biquad DF2T) | 5 cases (Peaking, LowShelf, HighShelf, LPF, HPF) | 5 cases (Nyquist f_s/2, 20Hz, Q=0.1, Q=10.0, +-24dB) | EQ + Gain + Delay | Multi-stage master bus EQ |
| F5 | Stereo Delay Effect | 5 cases (10ms, 100ms, 500ms, feedback=0, wet=0.5) | 5 cases (0ms delay, max buffer limit, feedback=0.99, wet=1.0) | Delay + Limiter + EQ | Ambient echo dub sequence |
| F6 | Master Lookahead Limiter | 5 cases (sub-threshold, threshold, overshoot, soft knee) | 5 cases (0dBFS ceiling, +12dB burst, instantaneous transient) | Summing + Limiter | Extreme multi-track peak limiting |
| F7 | RIFF/WAVE 16/24-bit IO | 5 cases (16-bit stereo, 24-bit stereo, 44.1k, 48k, headers) | 5 cases (empty data, extra chunks JUNK/LIST/bext, truncated) | Import + Decode + Playback | Roundtrip 24-bit lossless fidelity |
| F8 | Offline Mixdown Renderer | 5 cases (short clip, multi-track, silent intro, full song) | 5 cases (zero tracks, 1-frame duration, huge arrangement) | Summing + DSP + Mixdown | Complete studio project render |
| F9 | JNI Bridge & Telemetry | 5 cases (init, play, pause, seek, parameter mutation) | 5 cases (invalid track ID, out-of-range params, direct buffer null) | Telemetry poller + Audio callback | High-speed 60Hz telemetry pump |
| F10 | Compose UI & MVI State | 5 cases (StudioUiState transitions, action handling) | 5 cases (rapid clicks, edge-case frame positions, 0-track state) | Timeline + Mixer toggle | Full user studio session flow |
| F11 | Waveform Decimator | 5 cases (levels 0..4 peak computation, RMS extraction) | 5 cases (1-sample clip, silence, full-scale square wave) | Decimator + Canvas render | Multi-zoom timeline navigation |

## Real-World Application Scenarios (Tier 4)
| # | Scenario | Features Exercised | Complexity |
|---|----------|--------------------|------------|
| 1 | Multi-Track Pop Song Arrangement | F1, F2, F3, F4, F6, F7, F8 | High |
| 2 | Live Mic Vocal Recording & Overdub | F1, F2, F7, F9, F10 | High |
| 3 | Ambient Dub Delay & Swept Parametric EQ | F2, F4, F5, F6, F9, F10 | Medium |
| 4 | Offline 24-Bit Mastering Export | F2, F4, F6, F7, F8 | High |
| 5 | Live Studio Mixer Jam with 60Hz Telemetry | F2, F3, F9, F10, F11 | Medium |

## Test Runner Architecture
- C++ Engine Tests: Standalone GoogleTest / lightweight native test suite executed as part of CMake / Gradle `testDebugUnitTest`.
- Kotlin JVM Tests: JUnit 4 + Mockito / Robolectric unit tests in `app/src/test/java/com/android/daw/`.
- CI Verification: GitHub Actions workflow (`.github/workflows/build.yml`) runs `./gradlew testDebugUnitTest --stacktrace` and `./gradlew assembleDebug --stacktrace`.

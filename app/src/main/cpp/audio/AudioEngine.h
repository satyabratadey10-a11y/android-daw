#ifndef ANDROID_DAW_AUDIO_ENGINE_H
#define ANDROID_DAW_AUDIO_ENGINE_H

#include <vector>
#include <memory>
#include <atomic>
#include <string>
#include <mutex>
#include <cstdint>
#include <cstddef>
#include "AudioTrack.h"
#include "AudioClip.h"
#include "../dsp/MasterLimiter.h"

namespace daw {
namespace audio {

/**
 * Core Multi-Track Real-Time Audio Engine.
 *
 * Implements:
 * 1. Planar 32-bit floating-point audio processing bus.
 * 2. Multi-track additive summing with solo-in-place and mute handling.
 * 3. Lock-free real-time audio callback execution.
 * 4. Master bus limiter & IEC 60268-10 ballistics metering.
 * 5. Dynamic track management and transport synchronization.
 */
class AudioEngine {
public:
    static constexpr size_t kMaxTracks = 64;

    AudioEngine();
    ~AudioEngine();

    // Lifecycle
    void init(int sample_rate, int frames_per_burst);
    void release();
    [[nodiscard]] bool isInitialized() const noexcept { return is_initialized_; }

    // Transport Controls
    void play() noexcept;
    void pause() noexcept;
    void stop() noexcept;
    void seek(int64_t frame_position) noexcept;
    void setLoop(bool enabled, int64_t start_frame, int64_t end_frame) noexcept;
    void setTempo(double bpm) noexcept;

    [[nodiscard]] bool isPlaying() const noexcept { return is_playing_.load(std::memory_order_relaxed); }
    [[nodiscard]] int64_t getPlaybackPositionFrames() const noexcept { return playback_frame_.load(std::memory_order_relaxed); }
    [[nodiscard]] double getTempo() const noexcept { return bpm_.load(std::memory_order_relaxed); }
    [[nodiscard]] int getSampleRate() const noexcept { return sample_rate_; }
    [[nodiscard]] int getFramesPerBurst() const noexcept { return frames_per_burst_; }

    // Track Management
    int addTrack(const std::string& name);
    void removeTrack(int track_id);
    AudioTrack* getTrack(int track_id) noexcept;
    [[nodiscard]] size_t getTrackCount() const noexcept;

    void setTrackVolume(int track_id, float linear_gain) noexcept;
    void setTrackPan(int track_id, float pan_position) noexcept;
    void setTrackMute(int track_id, bool muted) noexcept;
    void setTrackSolo(int track_id, bool soloed) noexcept;
    void setTrackArmed(int track_id, bool armed) noexcept;

    // DSP Configuration
    void setTrackEqBand(int track_id, int band_index, int filter_type, float freq_hz, float gain_db, float q_factor) noexcept;
    void setTrackDelay(int track_id, float delay_ms, float feedback, float wet_dry) noexcept;
    void setMasterLimiter(float threshold_db, float ceiling_db, float release_ms) noexcept;

    [[nodiscard]] dsp::MasterLimiter& getMasterLimiter() noexcept { return master_limiter_; }
    [[nodiscard]] const dsp::MasterLimiter& getMasterLimiter() const noexcept { return master_limiter_; }

    // Telemetry & Level Metering
    void getMeteringData(float* out_floats, int num_tracks) noexcept;

    // File IO & Timeline
    bool loadClip(int track_id, int clip_id, const std::string& wav_file_path, int64_t start_offset_frames);
    bool startRecording(int track_id, const std::string& destination_wav_path);
    void stopRecording(int track_id);
    bool renderMixdown(const std::string& output_wav_path, int bit_depth, int64_t total_frames);

    // Audio Callback Render Entry Points
    // Interleaved stereo for AAudio output callback
    void renderAudio(float* output_interleaved, size_t num_frames) noexcept;

    // Planar stereo block rendering (used for offline mixdown & testing)
    void renderAudioPlanar(float* out_left, float* out_right, size_t num_frames, int64_t current_frame) noexcept;

    // Recording input feeder
    void pushRecordingAudio(const float* input_interleaved, size_t num_frames) noexcept;

private:
    void prepareBuffers(size_t max_frames);

    bool is_initialized_{false};
    int sample_rate_{48000};
    int frames_per_burst_{192};

    // Transport atomics
    std::atomic<bool> is_playing_{false};
    std::atomic<int64_t> playback_frame_{0};
    std::atomic<bool> loop_enabled_{false};
    std::atomic<int64_t> loop_start_frame_{0};
    std::atomic<int64_t> loop_end_frame_{0};
    std::atomic<double> bpm_{120.0};

    // Tracks collection (RT safe lock-free reading)
    std::mutex track_mutex_;
    std::vector<std::unique_ptr<AudioTrack>> tracks_;
    std::atomic<int> next_track_id_{1};

    // Master DSP & Metering
    dsp::MasterLimiter master_limiter_{48000.0f, 5.0f};
    std::atomic<float> master_peak_l_{0.0f};
    std::atomic<float> master_peak_r_{0.0f};
    std::atomic<float> master_rms_l_{0.0f};
    std::atomic<float> master_rms_r_{0.0f};

    // Pre-allocated planar mixing accumulators (zero allocation on RT thread)
    std::vector<float> master_buf_l_;
    std::vector<float> master_buf_r_;
};

} // namespace audio
} // namespace daw

#endif // ANDROID_DAW_AUDIO_ENGINE_H

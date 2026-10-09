#ifndef ANDROID_DAW_AUDIO_TRACK_H
#define ANDROID_DAW_AUDIO_TRACK_H

#include <string>
#include <vector>
#include <atomic>
#include <cstdint>
#include <cstddef>
#include <memory>
#include "AudioClip.h"
#include "SpscRingBuffer.h"
#include "../dsp/ParameterSmoother.h"
#include "../dsp/Panner.h"
#include "../dsp/ParametricEq.h"
#include "../dsp/DelayEffect.h"

namespace daw {
namespace audio {

/**
 * AudioTrack: Channel strip containing audio clips, DSP rack, gain/pan, and metering.
 */
class AudioTrack {
public:
    explicit AudioTrack(int id = 0, std::string name = "Track", float sample_rate = 48000.0f);
    ~AudioTrack() = default;

    // Non-copyable for RT safety and atomic integrity
    AudioTrack(const AudioTrack&) = delete;
    AudioTrack& operator=(const AudioTrack&) = delete;

    void prepare(size_t max_buffer_size, float sample_rate);
    void setSampleRate(float sample_rate);

    // Track ID and Name
    [[nodiscard]] int getId() const noexcept { return id_; }
    void setId(int id) noexcept { id_ = id; }

    [[nodiscard]] const std::string& getName() const noexcept { return name_; }
    void setName(std::string name) { name_ = std::move(name); }

    // Clip Management
    void addClip(AudioClip clip);
    bool removeClip(int clip_id);
    AudioClip* getClip(int clip_id) noexcept;
    void clearClips();
    [[nodiscard]] const std::vector<AudioClip>& getClips() const noexcept { return clips_; }
    [[nodiscard]] std::vector<AudioClip>& getClips() noexcept { return clips_; }

    // Volume & Pan Controls
    void setVolume(float linear_volume) noexcept;
    [[nodiscard]] float getVolume() const noexcept;

    void setPan(float pan) noexcept;
    [[nodiscard]] float getPan() const noexcept;

    // Solo, Mute, Arm Flags
    void setMuted(bool muted) noexcept { muted_.store(muted, std::memory_order_relaxed); }
    [[nodiscard]] bool isMuted() const noexcept { return muted_.load(std::memory_order_relaxed); }

    void setSoloed(bool soloed) noexcept { soloed_.store(soloed, std::memory_order_relaxed); }
    [[nodiscard]] bool isSoloed() const noexcept { return soloed_.load(std::memory_order_relaxed); }

    void setArmed(bool armed) noexcept { armed_.store(armed, std::memory_order_relaxed); }
    [[nodiscard]] bool isArmed() const noexcept { return armed_.load(std::memory_order_relaxed); }

    // DSP Rack Configuration
    void setEqBand(size_t band_idx, dsp::FilterType type, float freq_hz, float gain_db, float q_factor) noexcept;
    void setDelay(float delay_ms, float feedback, float wet_dry) noexcept;

    [[nodiscard]] dsp::ParametricEq& getParametricEq() noexcept { return parametric_eq_; }
    [[nodiscard]] const dsp::ParametricEq& getParametricEq() const noexcept { return parametric_eq_; }

    [[nodiscard]] dsp::DelayEffect& getDelay() noexcept { return delay_; }
    [[nodiscard]] const dsp::DelayEffect& getDelay() const noexcept { return delay_; }

    // Lock-free telemetry meters
    void getMetering(float& peak_l, float& peak_r, float& rms_l, float& rms_r) const noexcept;

    // Real-time audio rendering
    void renderBlock(int64_t timeline_frame, size_t num_frames,
                     float* out_left, float* out_right,
                     bool has_any_solo) noexcept;

    // Recording operations
    bool startRecording(const std::string& destination_wav_path);
    void stopRecording();
    [[nodiscard]] bool isRecording() const noexcept { return is_recording_.load(std::memory_order_relaxed); }
    [[nodiscard]] const std::string& getRecordingFilePath() const noexcept { return recording_path_; }
    void pushRecordingSamples(const float* interleaved, size_t num_frames) noexcept;
    SpscRingBuffer<float>& getRecordingRingBuffer() noexcept { return record_ring_buffer_; }

private:
    int id_{0};
    std::string name_;
    float sample_rate_{48000.0f};

    std::vector<AudioClip> clips_;

    // DSP processing chain
    dsp::ParameterSmoother volume_smoother_{1.0f};
    dsp::Panner panner_{48000.0f};
    dsp::ParametricEq parametric_eq_{48000.0f};
    dsp::DelayEffect delay_{48000.0f};

    // State atomics
    std::atomic<bool> muted_{false};
    std::atomic<bool> soloed_{false};
    std::atomic<bool> armed_{false};

    // Metering ballistics
    std::atomic<float> peak_l_{0.0f};
    std::atomic<float> peak_r_{0.0f};
    std::atomic<float> rms_l_{0.0f};
    std::atomic<float> rms_r_{0.0f};

    // Pre-allocated realtime audio buffers (zero heap allocation in callback)
    std::vector<float> track_buf_l_;
    std::vector<float> track_buf_r_;

    // Recording buffers & state
    std::atomic<bool> is_recording_{false};
    std::string recording_path_;
    SpscRingBuffer<float> record_ring_buffer_{65536}; // Power-of-two capacity ring buffer
};

} // namespace audio
} // namespace daw

#endif // ANDROID_DAW_AUDIO_TRACK_H

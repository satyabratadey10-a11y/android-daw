#include "AudioTrack.h"
#include <algorithm>
#include <cmath>

namespace daw {
namespace audio {

AudioTrack::AudioTrack(int id, std::string name, float sample_rate)
    : id_(id),
      name_(std::move(name)),
      sample_rate_(sample_rate > 0.0f ? sample_rate : 48000.0f),
      volume_smoother_(1.0f, sample_rate_),
      panner_(sample_rate_),
      parametric_eq_(sample_rate_),
      delay_(sample_rate_) {
    prepare(4096, sample_rate_);
}

void AudioTrack::prepare(size_t max_buffer_size, float sample_rate) {
    if (sample_rate > 0.0f) {
        sample_rate_ = sample_rate;
        setSampleRate(sample_rate_);
    }
    if (max_buffer_size == 0) {
        max_buffer_size = 4096;
    }
    track_buf_l_.assign(max_buffer_size, 0.0f);
    track_buf_r_.assign(max_buffer_size, 0.0f);
}

void AudioTrack::setSampleRate(float sample_rate) {
    if (sample_rate <= 0.0f) return;
    sample_rate_ = sample_rate;
    volume_smoother_.setSampleRate(sample_rate_);
    panner_.setSampleRate(sample_rate_);
    parametric_eq_.setSampleRate(sample_rate_);
    delay_.setSampleRate(sample_rate_);
}

void AudioTrack::addClip(AudioClip clip) {
    clips_.push_back(std::move(clip));
}

bool AudioTrack::removeClip(int clip_id) {
    auto it = std::remove_if(clips_.begin(), clips_.end(), [clip_id](const AudioClip& c) {
        return c.getId() == clip_id;
    });
    if (it != clips_.end()) {
        clips_.erase(it, clips_.end());
        return true;
    }
    return false;
}

AudioClip* AudioTrack::getClip(int clip_id) noexcept {
    for (auto& clip : clips_) {
        if (clip.getId() == clip_id) {
            return &clip;
        }
    }
    return nullptr;
}

void AudioTrack::clearClips() {
    clips_.clear();
}

void AudioTrack::setVolume(float linear_volume) noexcept {
    linear_volume = std::clamp(linear_volume, 0.0f, 4.0f); // Up to +12 dB
    volume_smoother_.setTarget(linear_volume);
}

float AudioTrack::getVolume() const noexcept {
    return volume_smoother_.getTarget();
}

void AudioTrack::setPan(float pan) noexcept {
    pan = std::clamp(pan, -1.0f, 1.0f);
    panner_.setPan(pan);
}

float AudioTrack::getPan() const noexcept {
    return panner_.getPan();
}

void AudioTrack::setEqBand(size_t band_idx, dsp::FilterType type, float freq_hz, float gain_db, float q_factor) noexcept {
    parametric_eq_.setBand(band_idx, type, freq_hz, gain_db, q_factor);
}

void AudioTrack::setDelay(float delay_ms, float feedback, float wet_dry) noexcept {
    delay_.setDelayTimeMs(delay_ms);
    delay_.setFeedback(feedback);
    delay_.setWetDry(wet_dry);
}

void AudioTrack::getMetering(float& peak_l, float& peak_r, float& rms_l, float& rms_r) const noexcept {
    peak_l = peak_l_.load(std::memory_order_relaxed);
    peak_r = peak_r_.load(std::memory_order_relaxed);
    rms_l = rms_l_.load(std::memory_order_relaxed);
    rms_r = rms_r_.load(std::memory_order_relaxed);
}

void AudioTrack::renderBlock(int64_t timeline_frame, size_t num_frames,
                             float* out_left, float* out_right,
                             bool has_any_solo) noexcept {
    if (num_frames == 0) return;

    // Decay metering on muted / inactive tracks
    const bool is_muted = muted_.load(std::memory_order_relaxed);
    const bool is_soloed = soloed_.load(std::memory_order_relaxed);

    if (is_muted || (has_any_solo && !is_soloed)) {
        // Fast decay metering
        peak_l_.store(peak_l_.load(std::memory_order_relaxed) * 0.85f, std::memory_order_relaxed);
        peak_r_.store(peak_r_.load(std::memory_order_relaxed) * 0.85f, std::memory_order_relaxed);
        rms_l_.store(rms_l_.load(std::memory_order_relaxed) * 0.85f, std::memory_order_relaxed);
        rms_r_.store(rms_r_.load(std::memory_order_relaxed) * 0.85f, std::memory_order_relaxed);
        return;
    }

    if (track_buf_l_.size() < num_frames) {
        track_buf_l_.resize(num_frames, 0.0f);
        track_buf_r_.resize(num_frames, 0.0f);
    }

    // 1. Clear pre-allocated track buffers
    std::fill_n(track_buf_l_.data(), num_frames, 0.0f);
    std::fill_n(track_buf_r_.data(), num_frames, 0.0f);

    // 2. Sum intersecting timeline clips into track buffers
    for (const auto& clip : clips_) {
        if (clip.intersects(timeline_frame, num_frames)) {
            clip.renderBlock(timeline_frame, num_frames, track_buf_l_.data(), track_buf_r_.data());
        }
    }

    // 3. Process Track DSP Effects Chain (EQ -> Delay -> Gain -> Panner)
    if (parametric_eq_.isEnabled()) {
        parametric_eq_.processStereo(track_buf_l_.data(), track_buf_r_.data(), num_frames);
    }

    if (delay_.isEnabled()) {
        delay_.processStereo(track_buf_l_.data(), track_buf_r_.data(), num_frames);
    }

    // Smooth volume gain application
    volume_smoother_.applyGainStereo(track_buf_l_.data(), track_buf_r_.data(), num_frames);

    // Constant-power stereo panning law
    panner_.process(track_buf_l_.data(), track_buf_r_.data(), num_frames);

    // 4. Calculate Peak & RMS levels with ballistic decay
    float block_peak_l = 0.0f;
    float block_peak_r = 0.0f;
    float sum_sq_l = 0.0f;
    float sum_sq_r = 0.0f;

    for (size_t i = 0; i < num_frames; ++i) {
        const float sl = std::abs(track_buf_l_[i]);
        const float sr = std::abs(track_buf_r_[i]);
        if (sl > block_peak_l) block_peak_l = sl;
        if (sr > block_peak_r) block_peak_r = sr;
        sum_sq_l += sl * sl;
        sum_sq_r += sr * sr;
    }

    const float block_rms_l = std::sqrt(sum_sq_l / static_cast<float>(num_frames));
    const float block_rms_r = std::sqrt(sum_sq_r / static_cast<float>(num_frames));

    // Ballistics: instant attack, smooth release
    const float prev_peak_l = peak_l_.load(std::memory_order_relaxed);
    const float prev_peak_r = peak_r_.load(std::memory_order_relaxed);
    peak_l_.store(std::max(block_peak_l, prev_peak_l * 0.90f), std::memory_order_relaxed);
    peak_r_.store(std::max(block_peak_r, prev_peak_r * 0.90f), std::memory_order_relaxed);

    const float prev_rms_l = rms_l_.load(std::memory_order_relaxed);
    const float prev_rms_r = rms_r_.load(std::memory_order_relaxed);
    rms_l_.store(std::max(block_rms_l, prev_rms_l * 0.90f), std::memory_order_relaxed);
    rms_r_.store(std::max(block_rms_r, prev_rms_r * 0.90f), std::memory_order_relaxed);

    // 5. Accumulate into master planar buffers
    for (size_t i = 0; i < num_frames; ++i) {
        if (out_left) out_left[i] += track_buf_l_[i];
        if (out_right) out_right[i] += track_buf_r_[i];
    }
}

bool AudioTrack::startRecording(const std::string& destination_wav_path) {
    if (destination_wav_path.empty()) {
        return false;
    }
    recording_path_ = destination_wav_path;
    record_ring_buffer_.reset();
    is_recording_.store(true, std::memory_order_release);
    return true;
}

void AudioTrack::stopRecording() {
    is_recording_.store(false, std::memory_order_release);
}

void AudioTrack::pushRecordingSamples(const float* interleaved, size_t num_frames) noexcept {
    if (!is_recording_.load(std::memory_order_relaxed) || !interleaved || num_frames == 0) {
        return;
    }
    // Interleaved stereo samples: num_frames * 2 samples
    record_ring_buffer_.write(interleaved, num_frames * 2);
}

} // namespace audio
} // namespace daw

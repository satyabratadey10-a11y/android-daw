#include "AudioEngine.h"
#include "MixdownRenderer.h"
#include <algorithm>
#include <cmath>

namespace daw {
namespace audio {

AudioEngine::AudioEngine() {
    prepareBuffers(4096);
}

AudioEngine::~AudioEngine() {
    release();
}

void AudioEngine::prepareBuffers(size_t max_frames) {
    if (max_frames == 0) max_frames = 4096;
    master_buf_l_.assign(max_frames, 0.0f);
    master_buf_r_.assign(max_frames, 0.0f);
}

void AudioEngine::init(int sample_rate, int frames_per_burst) {
    sample_rate_ = sample_rate > 0 ? sample_rate : 48000;
    frames_per_burst_ = frames_per_burst > 0 ? frames_per_burst : 192;

    master_limiter_.setSampleRate(static_cast<float>(sample_rate_));
    prepareBuffers(std::max(static_cast<size_t>(frames_per_burst_ * 4), size_t{4096}));

    std::lock_guard<std::mutex> lock(track_mutex_);
    for (auto& track : tracks_) {
        if (track) {
            track->prepare(master_buf_l_.size(), static_cast<float>(sample_rate_));
        }
    }

    is_initialized_ = true;
}

void AudioEngine::release() {
    stop();
    std::lock_guard<std::mutex> lock(track_mutex_);
    tracks_.clear();
    is_initialized_ = false;
}

void AudioEngine::play() noexcept {
    is_playing_.store(true, std::memory_order_release);
}

void AudioEngine::pause() noexcept {
    is_playing_.store(false, std::memory_order_release);
}

void AudioEngine::stop() noexcept {
    is_playing_.store(false, std::memory_order_release);
    playback_frame_.store(0, std::memory_order_release);
}

void AudioEngine::seek(int64_t frame_position) noexcept {
    if (frame_position < 0) frame_position = 0;
    playback_frame_.store(frame_position, std::memory_order_release);
}

void AudioEngine::setLoop(bool enabled, int64_t start_frame, int64_t end_frame) noexcept {
    if (end_frame < start_frame) {
        std::swap(start_frame, end_frame);
    }
    loop_start_frame_.store(start_frame, std::memory_order_release);
    loop_end_frame_.store(end_frame, std::memory_order_release);
    loop_enabled_.store(enabled, std::memory_order_release);
}

void AudioEngine::setTempo(double bpm) noexcept {
    if (bpm > 10.0 && bpm < 400.0) {
        bpm_.store(bpm, std::memory_order_release);
    }
}

int AudioEngine::addTrack(const std::string& name) {
    std::lock_guard<std::mutex> lock(track_mutex_);
    const int new_id = next_track_id_.fetch_add(1, std::memory_order_relaxed);
    auto track = std::make_unique<AudioTrack>(new_id, name, static_cast<float>(sample_rate_));
    track->prepare(master_buf_l_.size(), static_cast<float>(sample_rate_));
    tracks_.push_back(std::move(track));
    return new_id;
}

void AudioEngine::removeTrack(int track_id) {
    std::lock_guard<std::mutex> lock(track_mutex_);
    auto it = std::remove_if(tracks_.begin(), tracks_.end(), [track_id](const std::unique_ptr<AudioTrack>& t) {
        return t && t->getId() == track_id;
    });
    if (it != tracks_.end()) {
        tracks_.erase(it, tracks_.end());
    }
}

AudioTrack* AudioEngine::getTrack(int track_id) noexcept {
    for (auto& track : tracks_) {
        if (track && track->getId() == track_id) {
            return track.get();
        }
    }
    return nullptr;
}

size_t AudioEngine::getTrackCount() const noexcept {
    return tracks_.size();
}

void AudioEngine::setTrackVolume(int track_id, float linear_gain) noexcept {
    auto* track = getTrack(track_id);
    if (track) {
        track->setVolume(linear_gain);
    }
}

void AudioEngine::setTrackPan(int track_id, float pan_position) noexcept {
    auto* track = getTrack(track_id);
    if (track) {
        track->setPan(pan_position);
    }
}

void AudioEngine::setTrackMute(int track_id, bool muted) noexcept {
    auto* track = getTrack(track_id);
    if (track) {
        track->setMuted(muted);
    }
}

void AudioEngine::setTrackSolo(int track_id, bool soloed) noexcept {
    auto* track = getTrack(track_id);
    if (track) {
        track->setSoloed(soloed);
    }
}

void AudioEngine::setTrackArmed(int track_id, bool armed) noexcept {
    auto* track = getTrack(track_id);
    if (track) {
        track->setArmed(armed);
    }
}

void AudioEngine::setTrackEqBand(int track_id, int band_index, int filter_type, float freq_hz, float gain_db, float q_factor) noexcept {
    auto* track = getTrack(track_id);
    if (track && band_index >= 0 && band_index < static_cast<int>(dsp::ParametricEq::kNumBands)) {
        track->setEqBand(static_cast<size_t>(band_index),
                         static_cast<dsp::FilterType>(filter_type),
                         freq_hz, gain_db, q_factor);
    }
}

void AudioEngine::setTrackDelay(int track_id, float delay_ms, float feedback, float wet_dry) noexcept {
    auto* track = getTrack(track_id);
    if (track) {
        track->setDelay(delay_ms, feedback, wet_dry);
    }
}

void AudioEngine::setMasterLimiter(float threshold_db, float ceiling_db, float release_ms) noexcept {
    master_limiter_.setThresholdDb(threshold_db);
    master_limiter_.setCeilingDb(ceiling_db);
    master_limiter_.setReleaseMs(release_ms);
}

void AudioEngine::getMeteringData(float* out_floats, int num_tracks) noexcept {
    if (!out_floats) return;

    // Master meters
    out_floats[0] = master_peak_l_.load(std::memory_order_relaxed);
    out_floats[1] = master_peak_r_.load(std::memory_order_relaxed);
    out_floats[2] = master_rms_l_.load(std::memory_order_relaxed);
    out_floats[3] = master_rms_r_.load(std::memory_order_relaxed);
    out_floats[4] = master_limiter_.getCurrentGainReductionDb();

    // Per-track meters: layout [5 + 4*i, 5 + 4*i + 1, 5 + 4*i + 2, 5 + 4*i + 3]
    for (int i = 0; i < num_tracks; ++i) {
        const size_t offset = 5 + (i * 4);
        if (i < static_cast<int>(tracks_.size()) && tracks_[i]) {
            float pl, pr, rl, rr;
            tracks_[i]->getMetering(pl, pr, rl, rr);
            out_floats[offset + 0] = pl;
            out_floats[offset + 1] = pr;
            out_floats[offset + 2] = rl;
            out_floats[offset + 3] = rr;
        } else {
            out_floats[offset + 0] = 0.0f;
            out_floats[offset + 1] = 0.0f;
            out_floats[offset + 2] = 0.0f;
            out_floats[offset + 3] = 0.0f;
        }
    }
}

bool AudioEngine::loadClip(int track_id, int clip_id, const std::string& wav_file_path, int64_t start_offset_frames) {
    auto* track = getTrack(track_id);
    if (!track) return false;

    AudioClip clip(clip_id, "Clip", start_offset_frames);
    if (!clip.loadFromWav(wav_file_path)) {
        return false;
    }

    track->addClip(std::move(clip));
    return true;
}

bool AudioEngine::startRecording(int track_id, const std::string& destination_wav_path) {
    auto* track = getTrack(track_id);
    if (!track) return false;
    track->setArmed(true);
    return track->startRecording(destination_wav_path);
}

void AudioEngine::stopRecording(int track_id) {
    auto* track = getTrack(track_id);
    if (track) {
        track->stopRecording();
    }
}

void AudioEngine::pushRecordingAudio(const float* input_interleaved, size_t num_frames) noexcept {
    if (!input_interleaved || num_frames == 0) return;

    for (auto& track : tracks_) {
        if (track && track->isArmed() && track->isRecording()) {
            track->pushRecordingSamples(input_interleaved, num_frames);
        }
    }
}

bool AudioEngine::renderMixdown(const std::string& output_wav_path, int bit_depth, int64_t total_frames) {
    return MixdownRenderer::renderOffline(*this, output_wav_path, bit_depth, total_frames, nullptr);
}

void AudioEngine::renderAudio(float* output_interleaved, size_t num_frames) noexcept {
    if (!output_interleaved || num_frames == 0) return;

    if (!is_playing_.load(std::memory_order_relaxed)) {
        // Output silence when stopped / paused
        std::fill_n(output_interleaved, num_frames * 2, 0.0f);
        master_peak_l_.store(master_peak_l_.load(std::memory_order_relaxed) * 0.85f, std::memory_order_relaxed);
        master_peak_r_.store(master_peak_r_.load(std::memory_order_relaxed) * 0.85f, std::memory_order_relaxed);
        master_rms_l_.store(master_rms_l_.load(std::memory_order_relaxed) * 0.85f, std::memory_order_relaxed);
        master_rms_r_.store(master_rms_r_.load(std::memory_order_relaxed) * 0.85f, std::memory_order_relaxed);
        return;
    }

    if (master_buf_l_.size() < num_frames) {
        master_buf_l_.resize(num_frames, 0.0f);
        master_buf_r_.resize(num_frames, 0.0f);
    }

    const int64_t current_pos = playback_frame_.load(std::memory_order_relaxed);

    // 1. Render planar multi-track audio into master planar accumulators
    renderAudioPlanar(master_buf_l_.data(), master_buf_r_.data(), num_frames, current_pos);

    // 2. Interleave planar stereo into AAudio output buffer
    for (size_t i = 0; i < num_frames; ++i) {
        output_interleaved[2 * i] = master_buf_l_[i];
        output_interleaved[2 * i + 1] = master_buf_r_[i];
    }

    // 3. Advance timeline playhead with loop boundary wrapping
    int64_t next_pos = current_pos + static_cast<int64_t>(num_frames);
    if (loop_enabled_.load(std::memory_order_relaxed)) {
        const int64_t loop_start = loop_start_frame_.load(std::memory_order_relaxed);
        const int64_t loop_end = loop_end_frame_.load(std::memory_order_relaxed);
        if (loop_end > loop_start && next_pos >= loop_end) {
            next_pos = loop_start + ((next_pos - loop_start) % (loop_end - loop_start));
        }
    }
    playback_frame_.store(next_pos, std::memory_order_relaxed);
}

void AudioEngine::renderAudioPlanar(float* out_left, float* out_right, size_t num_frames, int64_t current_frame) noexcept {
    if (num_frames == 0) return;
    if (!out_left && !out_right) return;

    // 1. Clear destination accumulators
    if (out_left) std::fill_n(out_left, num_frames, 0.0f);
    if (out_right) std::fill_n(out_right, num_frames, 0.0f);

    // 2. Evaluate Solo-in-Place condition
    bool has_any_solo = false;
    for (const auto& track : tracks_) {
        if (track && track->isSoloed()) {
            has_any_solo = true;
            break;
        }
    }

    // 3. Multi-track planar summing
    for (auto& track : tracks_) {
        if (track) {
            track->renderBlock(current_frame, num_frames, out_left, out_right, has_any_solo);
        }
    }

    // 4. Master Lookahead Limiter & Soft Knee Saturation
    master_limiter_.processStereo(out_left, out_right, num_frames);

    // 5. Compute Master Peak & RMS Levels with ballistic decay
    float block_peak_l = 0.0f;
    float block_peak_r = 0.0f;
    float sum_sq_l = 0.0f;
    float sum_sq_r = 0.0f;

    for (size_t i = 0; i < num_frames; ++i) {
        const float sl = out_left ? std::abs(out_left[i]) : 0.0f;
        const float sr = out_right ? std::abs(out_right[i]) : 0.0f;
        if (sl > block_peak_l) block_peak_l = sl;
        if (sr > block_peak_r) block_peak_r = sr;
        sum_sq_l += sl * sl;
        sum_sq_r += sr * sr;
    }

    const float block_rms_l = std::sqrt(sum_sq_l / static_cast<float>(num_frames));
    const float block_rms_r = std::sqrt(sum_sq_r / static_cast<float>(num_frames));

    const float prev_peak_l = master_peak_l_.load(std::memory_order_relaxed);
    const float prev_peak_r = master_peak_r_.load(std::memory_order_relaxed);
    master_peak_l_.store(std::max(block_peak_l, prev_peak_l * 0.90f), std::memory_order_relaxed);
    master_peak_r_.store(std::max(block_peak_r, prev_peak_r * 0.90f), std::memory_order_relaxed);

    const float prev_rms_l = master_rms_l_.load(std::memory_order_relaxed);
    const float prev_rms_r = master_rms_r_.load(std::memory_order_relaxed);
    master_rms_l_.store(std::max(block_rms_l, prev_rms_l * 0.90f), std::memory_order_relaxed);
    master_rms_r_.store(std::max(block_rms_r, prev_rms_r * 0.90f), std::memory_order_relaxed);
}

} // namespace audio
} // namespace daw

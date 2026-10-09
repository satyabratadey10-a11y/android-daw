#include "AudioClip.h"
#include "WavFile.h"
#include <algorithm>

namespace daw {
namespace audio {

AudioClip::AudioClip(int id, std::string name, int64_t start_frame)
    : id_(id),
      name_(std::move(name)),
      start_frame_(start_frame) {
}

bool AudioClip::loadFromWav(const std::string& wav_file_path) {
    WavFile wav;
    if (!wav.load(wav_file_path)) {
        return false;
    }

    file_path_ = wav_file_path;
    source_frames_ = wav.getNumFrames();
    duration_frames_ = static_cast<int64_t>(source_frames_);

    audio_left_ = wav.getChannel(0);
    audio_right_ = wav.getChannel(1);

    if (audio_right_.empty() && !audio_left_.empty()) {
        audio_right_ = audio_left_;
    }

    return true;
}

void AudioClip::setAudioData(std::vector<float> left, std::vector<float> right) {
    source_frames_ = left.size();
    duration_frames_ = static_cast<int64_t>(source_frames_);
    audio_left_ = std::move(left);
    audio_right_ = std::move(right);
    if (audio_right_.empty() && !audio_left_.empty()) {
        audio_right_ = audio_left_;
    }
}

void AudioClip::setDurationFrames(int64_t duration_frames) noexcept {
    if (duration_frames < 0) duration_frames = 0;
    duration_frames_ = duration_frames;
}

bool AudioClip::intersects(int64_t timeline_frame, size_t num_frames) const noexcept {
    if (muted_ || duration_frames_ <= 0 || source_frames_ == 0) {
        return false;
    }
    const int64_t window_start = timeline_frame;
    const int64_t window_end = timeline_frame + static_cast<int64_t>(num_frames);

    const int64_t clip_start = start_frame_;
    const int64_t clip_end = start_frame_ + duration_frames_;

    return (window_start < clip_end) && (window_end > clip_start);
}

void AudioClip::renderBlock(int64_t timeline_frame, size_t num_frames,
                            float* out_left, float* out_right) const noexcept {
    if (muted_ || duration_frames_ <= 0 || source_frames_ == 0 || num_frames == 0) {
        return;
    }
    if (!out_left && !out_right) {
        return;
    }

    const int64_t window_start = timeline_frame;
    const int64_t window_end = timeline_frame + static_cast<int64_t>(num_frames);

    const int64_t clip_start = start_frame_;
    const int64_t clip_end = start_frame_ + duration_frames_;

    const int64_t overlap_start = std::max(window_start, clip_start);
    const int64_t overlap_end = std::min(window_end, clip_end);

    if (overlap_start >= overlap_end) {
        return;
    }

    const int64_t max_src_frame = static_cast<int64_t>(source_frames_);
    const float clip_gain = gain_;

    for (int64_t t = overlap_start; t < overlap_end; ++t) {
        const size_t out_idx = static_cast<size_t>(t - window_start);
        const int64_t src_idx = (t - clip_start) + source_offset_frames_;

        if (src_idx >= 0 && src_idx < max_src_frame) {
            if (out_left && !audio_left_.empty()) {
                out_left[out_idx] += audio_left_[src_idx] * clip_gain;
            }
            if (out_right && !audio_right_.empty()) {
                out_right[out_idx] += audio_right_[src_idx] * clip_gain;
            }
        }
    }
}

} // namespace audio
} // namespace daw

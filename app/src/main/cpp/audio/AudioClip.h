#ifndef ANDROID_DAW_AUDIO_CLIP_H
#define ANDROID_DAW_AUDIO_CLIP_H

#include <string>
#include <vector>
#include <cstdint>
#include <cstddef>
#include <memory>

namespace daw {
namespace audio {

/**
 * AudioClip: Represents an audio region placed on the timeline.
 * Supports non-destructive start offset, duration trimming, gain, and mute.
 */
class AudioClip {
public:
    AudioClip(int id = 0, std::string name = "", int64_t start_frame = 0);
    ~AudioClip() = default;

    // Load audio from WAV file using WavFile parser
    bool loadFromWav(const std::string& wav_file_path);

    // Set audio directly from memory buffers
    void setAudioData(std::vector<float> left, std::vector<float> right);

    // Test timeline intersection with [timeline_frame, timeline_frame + num_frames)
    [[nodiscard]] bool intersects(int64_t timeline_frame, size_t num_frames) const noexcept;

    // Render planar audio into destination buffers with additive summing
    void renderBlock(int64_t timeline_frame, size_t num_frames,
                     float* out_left, float* out_right) const noexcept;

    // Property accessors
    [[nodiscard]] int getId() const noexcept { return id_; }
    void setId(int id) noexcept { id_ = id; }

    [[nodiscard]] const std::string& getName() const noexcept { return name_; }
    void setName(std::string name) { name_ = std::move(name); }

    [[nodiscard]] const std::string& getFilePath() const noexcept { return file_path_; }

    [[nodiscard]] int64_t getStartFrame() const noexcept { return start_frame_; }
    void setStartFrame(int64_t start_frame) noexcept { start_frame_ = start_frame; }

    [[nodiscard]] int64_t getDurationFrames() const noexcept { return duration_frames_; }
    void setDurationFrames(int64_t duration_frames) noexcept;

    [[nodiscard]] int64_t getSourceOffsetFrames() const noexcept { return source_offset_frames_; }
    void setSourceOffsetFrames(int64_t offset_frames) noexcept { source_offset_frames_ = offset_frames; }

    [[nodiscard]] float getGain() const noexcept { return gain_; }
    void setGain(float gain) noexcept { gain_ = gain; }

    [[nodiscard]] bool isMuted() const noexcept { return muted_; }
    void setMuted(bool muted) noexcept { muted_ = muted; }

    [[nodiscard]] size_t getSourceFrames() const noexcept { return source_frames_; }
    [[nodiscard]] const std::vector<float>& getAudioLeft() const noexcept { return audio_left_; }
    [[nodiscard]] const std::vector<float>& getAudioRight() const noexcept { return audio_right_; }

private:
    int id_{0};
    std::string name_;
    std::string file_path_;

    int64_t start_frame_{0};
    int64_t duration_frames_{0};
    int64_t source_offset_frames_{0};

    float gain_{1.0f};
    bool muted_{false};

    size_t source_frames_{0};
    std::vector<float> audio_left_;
    std::vector<float> audio_right_;
};

} // namespace audio
} // namespace daw

#endif // ANDROID_DAW_AUDIO_CLIP_H

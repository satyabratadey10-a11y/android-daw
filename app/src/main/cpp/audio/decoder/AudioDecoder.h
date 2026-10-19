#ifndef ANDROID_DAW_AUDIO_DECODER_H
#define ANDROID_DAW_AUDIO_DECODER_H

#include <string>
#include <vector>
#include <cstdint>
#include <cstddef>

namespace daw {
namespace audio {

struct DecodedAudioLevel {
    int samples_per_block{64};
    std::vector<float> min_peaks;
    std::vector<float> max_peaks;
    std::vector<float> rms_peaks;
};

struct DecodedAudio {
    bool success{false};
    std::string error_message;
    uint32_t sample_rate{44100};
    uint16_t channels{2};
    size_t total_frames{0};
    std::vector<float> audio_left;
    std::vector<float> audio_right;
    std::vector<DecodedAudioLevel> pyramid_levels; // 64, 512, 4096
};

class AudioDecoder {
public:
    /**
     * Decode WAV, MP3, or FLAC file into float32 stereo audio and compute
     * a 3-level min/max peak decimation pyramid (64 / 512 / 4096 samples/block).
     *
     * @param file_path Absolute path to the audio file on disk
     * @param target_sample_rate Target sample rate to resample to (e.g. 44100 or 48000, 0 to keep native)
     */
    static DecodedAudio decodeFile(const std::string& file_path, uint32_t target_sample_rate = 0);

    /**
     * Compute 3-level decimation pyramid from in-memory planar audio
     */
    static std::vector<DecodedAudioLevel> computePyramid(const float* left, const float* right, size_t num_frames);

    /**
     * High-quality linear / cubic interpolation resampler
     */
    static void resample(const std::vector<float>& in_left,
                         const std::vector<float>& in_right,
                         uint32_t in_rate,
                         uint32_t out_rate,
                         std::vector<float>& out_left,
                         std::vector<float>& out_right);
};

} // namespace audio
} // namespace daw

#endif // ANDROID_DAW_AUDIO_DECODER_H

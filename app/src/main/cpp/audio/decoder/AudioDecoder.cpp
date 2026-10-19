#define DR_WAV_IMPLEMENTATION
#include "dr_wav.h"

#define DR_MP3_IMPLEMENTATION
#include "dr_mp3.h"

#define DR_FLAC_IMPLEMENTATION
#include "dr_flac.h"

#include "AudioDecoder.h"
#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <cstring>

namespace daw {
namespace audio {

enum class CodecType { None, Wav, Mp3, Flac };

static std::string getFileExtension(const std::string& path) {
    const size_t dot = path.find_last_of('.');
    if (dot == std::string::npos) return "";
    std::string ext = path.substr(dot + 1);
    std::transform(ext.begin(), ext.end(), ext.begin(), ::tolower);
    return ext;
}

DecodedAudio AudioDecoder::decodeFile(const std::string& file_path, uint32_t target_sample_rate) {
    DecodedAudio result;
    const std::string ext = getFileExtension(file_path);

    float* pSampleData = nullptr;
    unsigned int channels = 0;
    unsigned int sampleRate = 0;
    uint64_t totalFrameCount = 0;
    CodecType codec = CodecType::None;

    auto freeSampleData = [&](float* ptr) {
        if (!ptr) return;
        if (codec == CodecType::Wav) {
            drwav_free(ptr, nullptr);
        } else if (codec == CodecType::Mp3) {
            drmp3_free(ptr, nullptr);
        } else if (codec == CodecType::Flac) {
            drflac_free(ptr, nullptr);
        } else {
            std::free(ptr);
        }
    };

    if (ext == "wav" || ext == "wave") {
        drwav_uint64 frames = 0;
        pSampleData = drwav_open_file_and_read_pcm_frames_f32(file_path.c_str(), &channels, &sampleRate, &frames, nullptr);
        totalFrameCount = static_cast<uint64_t>(frames);
        codec = CodecType::Wav;
    } else if (ext == "mp3") {
        drmp3_config config;
        std::memset(&config, 0, sizeof(config));
        drmp3_uint64 frames = 0;
        pSampleData = drmp3_open_file_and_read_pcm_frames_f32(file_path.c_str(), &config, &frames, nullptr);
        if (pSampleData) {
            channels = config.channels;
            sampleRate = config.sampleRate;
            totalFrameCount = static_cast<uint64_t>(frames);
            codec = CodecType::Mp3;
        }
    } else if (ext == "flac") {
        drflac_uint64 frames = 0;
        pSampleData = drflac_open_file_and_read_pcm_frames_f32(file_path.c_str(), &channels, &sampleRate, &frames, nullptr);
        totalFrameCount = static_cast<uint64_t>(frames);
        codec = CodecType::Flac;
    } else {
        // Fallback: Attempt decoding with WAV then MP3 then FLAC
        drwav_uint64 w_frames = 0;
        pSampleData = drwav_open_file_and_read_pcm_frames_f32(file_path.c_str(), &channels, &sampleRate, &w_frames, nullptr);
        if (pSampleData) {
            totalFrameCount = static_cast<uint64_t>(w_frames);
            codec = CodecType::Wav;
        } else {
            drmp3_config config;
            std::memset(&config, 0, sizeof(config));
            drmp3_uint64 m_frames = 0;
            pSampleData = drmp3_open_file_and_read_pcm_frames_f32(file_path.c_str(), &config, &m_frames, nullptr);
            if (pSampleData) {
                channels = config.channels;
                sampleRate = config.sampleRate;
                totalFrameCount = static_cast<uint64_t>(m_frames);
                codec = CodecType::Mp3;
            } else {
                drflac_uint64 f_frames = 0;
                pSampleData = drflac_open_file_and_read_pcm_frames_f32(file_path.c_str(), &channels, &sampleRate, &f_frames, nullptr);
                if (pSampleData) {
                    totalFrameCount = static_cast<uint64_t>(f_frames);
                    codec = CodecType::Flac;
                }
            }
        }
    }

    if (!pSampleData || totalFrameCount == 0 || channels == 0 || sampleRate == 0) {
        result.success = false;
        result.error_message = "Failed to decode audio file or format unsupported: " + file_path;
        if (pSampleData) {
            freeSampleData(pSampleData);
        }
        return result;
    }

    // Unpack interleaved audio to planar stereo float32
    std::vector<float> raw_left(totalFrameCount);
    std::vector<float> raw_right(totalFrameCount);

    if (channels == 1) {
        // Mono to dual-mono
        for (size_t i = 0; i < totalFrameCount; ++i) {
            const float s = pSampleData[i];
            raw_left[i] = s;
            raw_right[i] = s;
        }
    } else if (channels == 2) {
        // Direct stereo
        for (size_t i = 0; i < totalFrameCount; ++i) {
            raw_left[i] = pSampleData[i * 2];
            raw_right[i] = pSampleData[i * 2 + 1];
        }
    } else {
        // Multichannel downmix to stereo
        for (size_t i = 0; i < totalFrameCount; ++i) {
            const size_t offset = i * channels;
            raw_left[i] = pSampleData[offset];
            raw_right[i] = pSampleData[offset + 1];
        }
    }

    freeSampleData(pSampleData);

    // Resample if target sample rate specified and differs
    if (target_sample_rate > 0 && target_sample_rate != sampleRate) {
        resample(raw_left, raw_right, sampleRate, target_sample_rate, result.audio_left, result.audio_right);
        result.sample_rate = target_sample_rate;
        result.total_frames = result.audio_left.size();
    } else {
        result.audio_left = std::move(raw_left);
        result.audio_right = std::move(raw_right);
        result.sample_rate = sampleRate;
        result.total_frames = totalFrameCount;
    }

    result.channels = 2;
    result.success = true;

    // Compute 3-level decimation pyramid (64 / 512 / 4096)
    result.pyramid_levels = computePyramid(result.audio_left.data(), result.audio_right.data(), result.total_frames);

    return result;
}

std::vector<DecodedAudioLevel> AudioDecoder::computePyramid(const float* left, const float* right, size_t num_frames) {
    std::vector<DecodedAudioLevel> levels;
    if (!left || num_frames == 0) return levels;

    const int block_sizes[] = {64, 512, 4096};

    for (int block_size : block_sizes) {
        DecodedAudioLevel level;
        level.samples_per_block = block_size;
        const size_t block_count = (num_frames + block_size - 1) / block_size;
        level.min_peaks.resize(block_count);
        level.max_peaks.resize(block_count);
        level.rms_peaks.resize(block_count);

        for (size_t b = 0; b < block_count; ++b) {
            const size_t start = b * block_size;
            const size_t end = std::min(num_frames, start + block_size);

            float min_val = 0.0f;
            float max_val = 0.0f;
            double sum_sq = 0.0;

            for (size_t i = start; i < end; ++i) {
                const float s = right ? 0.5f * (left[i] + right[i]) : left[i];
                if (s < min_val) min_val = s;
                if (s > max_val) max_val = s;
                sum_sq += static_cast<double>(s) * static_cast<double>(s);
            }

            const size_t count = end - start;
            level.min_peaks[b] = min_val;
            level.max_peaks[b] = max_val;
            level.rms_peaks[b] = count > 0 ? static_cast<float>(std::sqrt(sum_sq / count)) : 0.0f;
        }

        levels.push_back(std::move(level));
    }

    return levels;
}

void AudioDecoder::resample(const std::vector<float>& in_left,
                            const std::vector<float>& in_right,
                            uint32_t in_rate,
                            uint32_t out_rate,
                            std::vector<float>& out_left,
                            std::vector<float>& out_right) {
    if (in_left.empty() || in_rate == 0 || out_rate == 0) return;

    const double ratio = static_cast<double>(in_rate) / static_cast<double>(out_rate);
    const size_t out_frames = static_cast<size_t>(std::ceil(in_left.size() / ratio));

    out_left.resize(out_frames);
    out_right.resize(out_frames);

    const size_t in_size = in_left.size();

    for (size_t i = 0; i < out_frames; ++i) {
        const double src_pos = i * ratio;
        const size_t idx = static_cast<size_t>(src_pos);
        const float frac = static_cast<float>(src_pos - idx);

        if (idx + 1 < in_size) {
            out_left[i] = in_left[idx] * (1.0f - frac) + in_left[idx + 1] * frac;
            out_right[i] = in_right[idx] * (1.0f - frac) + in_right[idx + 1] * frac;
        } else if (idx < in_size) {
            out_left[i] = in_left[idx];
            out_right[i] = in_right[idx];
        } else {
            out_left[i] = 0.0f;
            out_right[i] = 0.0f;
        }
    }
}

} // namespace audio
} // namespace daw

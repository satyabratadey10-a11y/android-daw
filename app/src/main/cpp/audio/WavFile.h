#ifndef ANDROID_DAW_WAV_FILE_H
#define ANDROID_DAW_WAV_FILE_H

#include <cstdint>
#include <cstddef>
#include <string>
#include <vector>
#include <fstream>
#include <memory>

namespace daw {
namespace audio {

/**
 * High-performance PRNG for Triangular Probability Density Function (TPDF) Dither.
 */
class TpdfDitherGenerator {
public:
    explicit TpdfDitherGenerator(uint32_t seed = 0x9E3779B9u) : state_(seed != 0 ? seed : 0x12345678u) {}

    inline float nextDither16() noexcept {
        const float u1 = nextUniform();
        const float u2 = nextUniform();
        return (u1 - u2) * (1.0f / 32768.0f);
    }

    inline float nextDither24() noexcept {
        const float u1 = nextUniform();
        const float u2 = nextUniform();
        return (u1 - u2) * (1.0f / 8388608.0f);
    }

private:
    inline float nextUniform() noexcept {
        state_ ^= state_ << 13;
        state_ ^= state_ >> 17;
        state_ ^= state_ << 5;
        return static_cast<float>(state_) * (1.0f / 4294967296.0f);
    }

    uint32_t state_;
};

/**
 * WAV RIFF file header specification.
 */
struct WavFormatInfo {
    uint16_t audio_format{1};       // 1 = PCM, 3 = IEEE Float
    uint16_t num_channels{2};       // 1 = Mono, 2 = Stereo
    uint32_t sample_rate{48000};
    uint32_t byte_rate{192000};
    uint16_t block_align{4};
    uint16_t bits_per_sample{16};
    size_t total_frames{0};
};

/**
 * Streaming WAV File Writer with TPDF dithering.
 * Supports 16-bit and 24-bit PCM export.
 */
class WavWriter {
public:
    WavWriter();
    WavWriter(const std::string& file_path, uint32_t sample_rate, uint16_t num_channels = 2, int bits_per_sample = 24);
    ~WavWriter();

    bool open(const std::string& file_path, uint32_t sample_rate, uint16_t num_channels = 2, int bits_per_sample = 24);
    [[nodiscard]] bool isOpen() const noexcept { return is_open_; }

    // Write planar stereo audio
    bool writePlanar(const float* left, const float* right, size_t num_frames);

    // Write interleaved stereo audio
    bool writeInterleaved(const float* interleaved, size_t num_frames);

    // Finalize header and close file
    bool close();

    [[nodiscard]] size_t getFramesWritten() const noexcept { return frames_written_; }

private:
    void writeHeaderPlaceholder();
    void updateHeaderSizes();

    std::ofstream file_stream_;
    std::string file_path_;
    WavFormatInfo format_;
    bool is_open_{false};
    size_t frames_written_{0};
    size_t data_bytes_written_{0};
    TpdfDitherGenerator dither_l_{0x1A2B3C4Du};
    TpdfDitherGenerator dither_r_{0x5E6F7A8Bu};
    std::vector<uint8_t> write_buffer_;
};

/**
 * WAV File Reader and In-Memory Container.
 * Parses standard and broadcast WAV files, skips metadata chunks (JUNK, bext, LIST),
 * and unpacks 16-bit, 24-bit, and 32-bit PCM/Float into planar float32 buffers.
 */
class WavFile {
public:
    WavFile() = default;
    explicit WavFile(const std::string& file_path);

    bool load(const std::string& file_path);
    bool save(const std::string& file_path, int bits_per_sample = 24) const;

    [[nodiscard]] bool isValid() const noexcept { return !channel_data_.empty() && total_frames_ > 0; }
    [[nodiscard]] uint32_t getSampleRate() const noexcept { return format_.sample_rate; }
    [[nodiscard]] uint16_t getNumChannels() const noexcept { return format_.num_channels; }
    [[nodiscard]] uint16_t getBitsPerSample() const noexcept { return format_.bits_per_sample; }
    [[nodiscard]] size_t getNumFrames() const noexcept { return total_frames_; }

    [[nodiscard]] const float* getChannelData(size_t channel) const noexcept;
    [[nodiscard]] const std::vector<float>& getChannel(size_t channel) const;

    void setAudioData(std::vector<float> left, std::vector<float> right, uint32_t sample_rate);

    // Static convenience helpers
    static bool writeWavFile(const std::string& path,
                             const float* left, const float* right,
                             size_t num_frames,
                             uint32_t sample_rate = 48000,
                             int bits_per_sample = 24);

private:
    WavFormatInfo format_;
    size_t total_frames_{0};
    std::vector<std::vector<float>> channel_data_;
};

} // namespace audio
} // namespace daw

#endif // ANDROID_DAW_WAV_FILE_H

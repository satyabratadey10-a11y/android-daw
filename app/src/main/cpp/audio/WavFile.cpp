#include "WavFile.h"
#include <cstring>
#include <algorithm>
#include <cmath>

namespace daw {
namespace audio {

// =============================================================================
// WavWriter Implementation
// =============================================================================

WavWriter::WavWriter() = default;

WavWriter::WavWriter(const std::string& file_path, uint32_t sample_rate, uint16_t num_channels, int bits_per_sample) {
    open(file_path, sample_rate, num_channels, bits_per_sample);
}

WavWriter::~WavWriter() {
    if (is_open_) {
        close();
    }
}

bool WavWriter::open(const std::string& file_path, uint32_t sample_rate, uint16_t num_channels, int bits_per_sample) {
    if (is_open_) {
        close();
    }

    file_path_ = file_path;
    format_.audio_format = 1; // PCM Integer
    format_.num_channels = (num_channels == 1) ? 1 : 2;
    format_.sample_rate = sample_rate > 0 ? sample_rate : 48000;
    format_.bits_per_sample = (bits_per_sample == 16) ? 16 : 24;
    format_.block_align = static_cast<uint16_t>(format_.num_channels * (format_.bits_per_sample / 8));
    format_.byte_rate = format_.sample_rate * format_.block_align;
    format_.total_frames = 0;

    frames_written_ = 0;
    data_bytes_written_ = 0;

    file_stream_.open(file_path_, std::ios::binary | std::ios::trunc | std::ios::out);
    if (!file_stream_.is_open()) {
        is_open_ = false;
        return false;
    }

    writeHeaderPlaceholder();
    is_open_ = file_stream_.good();
    return is_open_;
}

void WavWriter::writeHeaderPlaceholder() {
    // 44-byte standard RIFF header
    const char riff_id[4] = {'R', 'I', 'F', 'F'};
    const uint32_t zero_size = 0;
    const char wave_id[4] = {'W', 'A', 'V', 'E'};
    const char fmt_id[4] = {'f', 'm', 't', ' '};
    const uint32_t fmt_size = 16;
    const char data_id[4] = {'d', 'a', 't', 'a'};

    file_stream_.write(riff_id, 4);
    file_stream_.write(reinterpret_cast<const char*>(&zero_size), 4); // File size placeholder
    file_stream_.write(wave_id, 4);

    file_stream_.write(fmt_id, 4);
    file_stream_.write(reinterpret_cast<const char*>(&fmt_size), 4);
    file_stream_.write(reinterpret_cast<const char*>(&format_.audio_format), 2);
    file_stream_.write(reinterpret_cast<const char*>(&format_.num_channels), 2);
    file_stream_.write(reinterpret_cast<const char*>(&format_.sample_rate), 4);
    file_stream_.write(reinterpret_cast<const char*>(&format_.byte_rate), 4);
    file_stream_.write(reinterpret_cast<const char*>(&format_.block_align), 2);
    file_stream_.write(reinterpret_cast<const char*>(&format_.bits_per_sample), 2);

    file_stream_.write(data_id, 4);
    file_stream_.write(reinterpret_cast<const char*>(&zero_size), 4); // Data size placeholder
}

bool WavWriter::writePlanar(const float* left, const float* right, size_t num_frames) {
    if (!is_open_ || num_frames == 0) return false;

    const size_t bytes_per_sample = format_.bits_per_sample / 8;
    const size_t total_bytes = num_frames * format_.num_channels * bytes_per_sample;

    if (write_buffer_.size() < total_bytes) {
        write_buffer_.resize(total_bytes);
    }

    uint8_t* dest = write_buffer_.data();

    if (format_.bits_per_sample == 16) {
        if (format_.num_channels == 2) {
            for (size_t i = 0; i < num_frames; ++i) {
                const float in_l = left ? left[i] : 0.0f;
                const float in_r = right ? right[i] : 0.0f;

                const float d_l = dither_l_.nextDither16();
                const float d_r = dither_r_.nextDither16();

                const int32_t s_l = std::clamp(static_cast<int32_t>(std::round((in_l + d_l) * 32767.0f)), -32768, 32767);
                const int32_t s_r = std::clamp(static_cast<int32_t>(std::round((in_r + d_r) * 32767.0f)), -32768, 32767);

                const int16_t pcm_l = static_cast<int16_t>(s_l);
                const int16_t pcm_r = static_cast<int16_t>(s_r);

                std::memcpy(dest, &pcm_l, 2);
                dest += 2;
                std::memcpy(dest, &pcm_r, 2);
                dest += 2;
            }
        } else {
            for (size_t i = 0; i < num_frames; ++i) {
                const float in_mono = left ? left[i] : (right ? right[i] : 0.0f);
                const float d = dither_l_.nextDither16();
                const int32_t s = std::clamp(static_cast<int32_t>(std::round((in_mono + d) * 32767.0f)), -32768, 32767);
                const int16_t pcm = static_cast<int16_t>(s);
                std::memcpy(dest, &pcm, 2);
                dest += 2;
            }
        }
    } else { // 24-bit PCM
        if (format_.num_channels == 2) {
            for (size_t i = 0; i < num_frames; ++i) {
                const float in_l = left ? left[i] : 0.0f;
                const float in_r = right ? right[i] : 0.0f;

                const float d_l = dither_l_.nextDither24();
                const float d_r = dither_r_.nextDither24();

                const int32_t s_l = std::clamp(static_cast<int32_t>(std::round((in_l + d_l) * 8388607.0f)), -8388608, 8388607);
                const int32_t s_r = std::clamp(static_cast<int32_t>(std::round((in_r + d_r) * 8388607.0f)), -8388608, 8388607);

                dest[0] = static_cast<uint8_t>(s_l & 0xFF);
                dest[1] = static_cast<uint8_t>((s_l >> 8) & 0xFF);
                dest[2] = static_cast<uint8_t>((s_l >> 16) & 0xFF);
                dest += 3;

                dest[0] = static_cast<uint8_t>(s_r & 0xFF);
                dest[1] = static_cast<uint8_t>((s_r >> 8) & 0xFF);
                dest[2] = static_cast<uint8_t>((s_r >> 16) & 0xFF);
                dest += 3;
            }
        } else {
            for (size_t i = 0; i < num_frames; ++i) {
                const float in_mono = left ? left[i] : (right ? right[i] : 0.0f);
                const float d = dither_l_.nextDither24();
                const int32_t s = std::clamp(static_cast<int32_t>(std::round((in_mono + d) * 8388607.0f)), -8388608, 8388607);

                dest[0] = static_cast<uint8_t>(s & 0xFF);
                dest[1] = static_cast<uint8_t>((s >> 8) & 0xFF);
                dest[2] = static_cast<uint8_t>((s >> 16) & 0xFF);
                dest += 3;
            }
        }
    }

    file_stream_.write(reinterpret_cast<const char*>(write_buffer_.data()), total_bytes);
    if (!file_stream_.good()) {
        return false;
    }

    frames_written_ += num_frames;
    data_bytes_written_ += total_bytes;
    return true;
}

bool WavWriter::writeInterleaved(const float* interleaved, size_t num_frames) {
    if (!is_open_ || !interleaved || num_frames == 0) return false;

    const size_t bytes_per_sample = format_.bits_per_sample / 8;
    const size_t total_bytes = num_frames * format_.num_channels * bytes_per_sample;

    if (write_buffer_.size() < total_bytes) {
        write_buffer_.resize(total_bytes);
    }

    uint8_t* dest = write_buffer_.data();

    if (format_.bits_per_sample == 16) {
        if (format_.num_channels == 2) {
            for (size_t i = 0; i < num_frames; ++i) {
                const float in_l = interleaved[2 * i];
                const float in_r = interleaved[2 * i + 1];

                const float d_l = dither_l_.nextDither16();
                const float d_r = dither_r_.nextDither16();

                const int32_t s_l = std::clamp(static_cast<int32_t>(std::round((in_l + d_l) * 32767.0f)), -32768, 32767);
                const int32_t s_r = std::clamp(static_cast<int32_t>(std::round((in_r + d_r) * 32767.0f)), -32768, 32767);

                const int16_t pcm_l = static_cast<int16_t>(s_l);
                const int16_t pcm_r = static_cast<int16_t>(s_r);

                std::memcpy(dest, &pcm_l, 2);
                dest += 2;
                std::memcpy(dest, &pcm_r, 2);
                dest += 2;
            }
        } else {
            for (size_t i = 0; i < num_frames; ++i) {
                const float in = interleaved[i];
                const float d = dither_l_.nextDither16();
                const int32_t s = std::clamp(static_cast<int32_t>(std::round((in + d) * 32767.0f)), -32768, 32767);
                const int16_t pcm = static_cast<int16_t>(s);
                std::memcpy(dest, &pcm, 2);
                dest += 2;
            }
        }
    } else { // 24-bit
        if (format_.num_channels == 2) {
            for (size_t i = 0; i < num_frames; ++i) {
                const float in_l = interleaved[2 * i];
                const float in_r = interleaved[2 * i + 1];

                const float d_l = dither_l_.nextDither24();
                const float d_r = dither_r_.nextDither24();

                const int32_t s_l = std::clamp(static_cast<int32_t>(std::round((in_l + d_l) * 8388607.0f)), -8388608, 8388607);
                const int32_t s_r = std::clamp(static_cast<int32_t>(std::round((in_r + d_r) * 8388607.0f)), -8388608, 8388607);

                dest[0] = static_cast<uint8_t>(s_l & 0xFF);
                dest[1] = static_cast<uint8_t>((s_l >> 8) & 0xFF);
                dest[2] = static_cast<uint8_t>((s_l >> 16) & 0xFF);
                dest += 3;

                dest[0] = static_cast<uint8_t>(s_r & 0xFF);
                dest[1] = static_cast<uint8_t>((s_r >> 8) & 0xFF);
                dest[2] = static_cast<uint8_t>((s_r >> 16) & 0xFF);
                dest += 3;
            }
        } else {
            for (size_t i = 0; i < num_frames; ++i) {
                const float in = interleaved[i];
                const float d = dither_l_.nextDither24();
                const int32_t s = std::clamp(static_cast<int32_t>(std::round((in + d) * 8388607.0f)), -8388608, 8388607);

                dest[0] = static_cast<uint8_t>(s & 0xFF);
                dest[1] = static_cast<uint8_t>((s >> 8) & 0xFF);
                dest[2] = static_cast<uint8_t>((s >> 16) & 0xFF);
                dest += 3;
            }
        }
    }

    file_stream_.write(reinterpret_cast<const char*>(write_buffer_.data()), total_bytes);
    if (!file_stream_.good()) return false;

    frames_written_ += num_frames;
    data_bytes_written_ += total_bytes;
    return true;
}

void WavWriter::updateHeaderSizes() {
    if (!file_stream_.is_open()) return;

    // RIFF chunk size = 36 + data_bytes_written_
    const uint32_t riff_chunk_size = static_cast<uint32_t>(36 + data_bytes_written_);
    file_stream_.seekp(4, std::ios::beg);
    file_stream_.write(reinterpret_cast<const char*>(&riff_chunk_size), 4);

    // data subchunk size = data_bytes_written_
    const uint32_t data_chunk_size = static_cast<uint32_t>(data_bytes_written_);
    file_stream_.seekp(40, std::ios::beg);
    file_stream_.write(reinterpret_cast<const char*>(&data_chunk_size), 4);

    file_stream_.seekp(0, std::ios::end);
    file_stream_.flush();
}

bool WavWriter::close() {
    if (!is_open_) return true;

    updateHeaderSizes();
    file_stream_.close();
    is_open_ = false;
    return true;
}

// =============================================================================
// WavFile Implementation
// =============================================================================

WavFile::WavFile(const std::string& file_path) {
    load(file_path);
}

bool WavFile::load(const std::string& file_path) {
    channel_data_.clear();
    total_frames_ = 0;

    std::ifstream stream(file_path, std::ios::binary);
    if (!stream.is_open()) {
        return false;
    }

    char riff_id[4];
    stream.read(riff_id, 4);
    if (std::memcmp(riff_id, "RIFF", 4) != 0) {
        return false;
    }

    uint32_t riff_size = 0;
    stream.read(reinterpret_cast<char*>(&riff_size), 4);

    char wave_id[4];
    stream.read(wave_id, 4);
    if (std::memcmp(wave_id, "WAVE", 4) != 0) {
        return false;
    }

    bool fmt_found = false;
    bool data_found = false;

    while (stream.good() && !data_found) {
        char chunk_id[4];
        uint32_t chunk_size = 0;

        stream.read(chunk_id, 4);
        stream.read(reinterpret_cast<char*>(&chunk_size), 4);
        if (stream.gcount() < 4) {
            break;
        }

        if (std::memcmp(chunk_id, "fmt ", 4) == 0) {
            stream.read(reinterpret_cast<char*>(&format_.audio_format), 2);
            stream.read(reinterpret_cast<char*>(&format_.num_channels), 2);
            stream.read(reinterpret_cast<char*>(&format_.sample_rate), 4);
            stream.read(reinterpret_cast<char*>(&format_.byte_rate), 4);
            stream.read(reinterpret_cast<char*>(&format_.block_align), 2);
            stream.read(reinterpret_cast<char*>(&format_.bits_per_sample), 2);

            // Skip any extended header bytes
            if (chunk_size > 16) {
                stream.seekg(chunk_size - 16, std::ios::cur);
            }
            fmt_found = true;
        } else if (std::memcmp(chunk_id, "data", 4) == 0) {
            if (!fmt_found) {
                // Invalid WAV: data before fmt
                return false;
            }

            const size_t bytes_per_sample = format_.bits_per_sample / 8;
            if (bytes_per_sample == 0 || format_.num_channels == 0) {
                return false;
            }

            const size_t bytes_per_frame = format_.num_channels * bytes_per_sample;
            total_frames_ = chunk_size / bytes_per_frame;

            std::vector<uint8_t> raw_pcm(chunk_size);
            stream.read(reinterpret_cast<char*>(raw_pcm.data()), chunk_size);

            channel_data_.resize(2); // Always prepare stereo channels
            channel_data_[0].resize(total_frames_);
            channel_data_[1].resize(total_frames_);

            const uint8_t* src = raw_pcm.data();

            if (format_.audio_format == 1) { // PCM Integer
                if (format_.bits_per_sample == 16) {
                    if (format_.num_channels == 2) {
                        for (size_t i = 0; i < total_frames_; ++i) {
                            int16_t s_l, s_r;
                            std::memcpy(&s_l, src, 2);
                            src += 2;
                            std::memcpy(&s_r, src, 2);
                            src += 2;
                            channel_data_[0][i] = static_cast<float>(s_l) / 32768.0f;
                            channel_data_[1][i] = static_cast<float>(s_r) / 32768.0f;
                        }
                    } else { // Mono
                        for (size_t i = 0; i < total_frames_; ++i) {
                            int16_t s;
                            std::memcpy(&s, src, 2);
                            src += 2;
                            const float mono_val = static_cast<float>(s) / 32768.0f;
                            channel_data_[0][i] = mono_val;
                            channel_data_[1][i] = mono_val;
                        }
                    }
                } else if (format_.bits_per_sample == 24) {
                    if (format_.num_channels == 2) {
                        for (size_t i = 0; i < total_frames_; ++i) {
                            int32_t s_l = static_cast<int32_t>(src[0]) |
                                          (static_cast<int32_t>(src[1]) << 8) |
                                          (static_cast<int32_t>(src[2]) << 16);
                            if (s_l & 0x800000) s_l |= 0xFF000000;
                            src += 3;

                            int32_t s_r = static_cast<int32_t>(src[0]) |
                                          (static_cast<int32_t>(src[1]) << 8) |
                                          (static_cast<int32_t>(src[2]) << 16);
                            if (s_r & 0x800000) s_r |= 0xFF000000;
                            src += 3;

                            channel_data_[0][i] = static_cast<float>(s_l) / 8388608.0f;
                            channel_data_[1][i] = static_cast<float>(s_r) / 8388608.0f;
                        }
                    } else { // Mono
                        for (size_t i = 0; i < total_frames_; ++i) {
                            int32_t s = static_cast<int32_t>(src[0]) |
                                        (static_cast<int32_t>(src[1]) << 8) |
                                        (static_cast<int32_t>(src[2]) << 16);
                            if (s & 0x800000) s |= 0xFF000000;
                            src += 3;
                            const float mono_val = static_cast<float>(s) / 8388608.0f;
                            channel_data_[0][i] = mono_val;
                            channel_data_[1][i] = mono_val;
                        }
                    }
                } else if (format_.bits_per_sample == 32) {
                    if (format_.num_channels == 2) {
                        for (size_t i = 0; i < total_frames_; ++i) {
                            int32_t s_l, s_r;
                            std::memcpy(&s_l, src, 4);
                            src += 4;
                            std::memcpy(&s_r, src, 4);
                            src += 4;
                            channel_data_[0][i] = static_cast<float>(s_l) / 2147483648.0f;
                            channel_data_[1][i] = static_cast<float>(s_r) / 2147483648.0f;
                        }
                    } else {
                        for (size_t i = 0; i < total_frames_; ++i) {
                            int32_t s;
                            std::memcpy(&s, src, 4);
                            src += 4;
                            const float mono_val = static_cast<float>(s) / 2147483648.0f;
                            channel_data_[0][i] = mono_val;
                            channel_data_[1][i] = mono_val;
                        }
                    }
                }
            } else if (format_.audio_format == 3) { // IEEE Float 32-bit
                if (format_.num_channels == 2) {
                    for (size_t i = 0; i < total_frames_; ++i) {
                        float s_l, s_r;
                        std::memcpy(&s_l, src, 4);
                        src += 4;
                        std::memcpy(&s_r, src, 4);
                        src += 4;
                        channel_data_[0][i] = s_l;
                        channel_data_[1][i] = s_r;
                    }
                } else {
                    for (size_t i = 0; i < total_frames_; ++i) {
                        float s;
                        std::memcpy(&s, src, 4);
                        src += 4;
                        channel_data_[0][i] = s;
                        channel_data_[1][i] = s;
                    }
                }
            } else {
                return false; // Unsupported compression
            }

            format_.total_frames = total_frames_;
            data_found = true;
        } else {
            // Metadata chunk (JUNK, bext, LIST, etc.) -> safely skip chunk payload
            stream.seekg(chunk_size, std::ios::cur);
            // Handle chunk padding to even byte boundary
            if (chunk_size % 2 != 0) {
                stream.seekg(1, std::ios::cur);
            }
        }
    }

    return fmt_found && data_found;
}

bool WavFile::save(const std::string& file_path, int bits_per_sample) const {
    if (!isValid()) return false;

    return writeWavFile(file_path,
                        channel_data_[0].data(),
                        channel_data_[1].data(),
                        total_frames_,
                        format_.sample_rate,
                        bits_per_sample);
}

const float* WavFile::getChannelData(size_t channel) const noexcept {
    if (channel < channel_data_.size() && !channel_data_[channel].empty()) {
        return channel_data_[channel].data();
    }
    return nullptr;
}

const std::vector<float>& WavFile::getChannel(size_t channel) const {
    static const std::vector<float> empty_vec;
    if (channel < channel_data_.size()) {
        return channel_data_[channel];
    }
    return empty_vec;
}

void WavFile::setAudioData(std::vector<float> left, std::vector<float> right, uint32_t sample_rate) {
    total_frames_ = left.size();
    if (right.size() < total_frames_) {
        right.resize(total_frames_, 0.0f);
    }

    channel_data_.resize(2);
    channel_data_[0] = std::move(left);
    channel_data_[1] = std::move(right);

    format_.num_channels = 2;
    format_.sample_rate = sample_rate > 0 ? sample_rate : 48000;
    format_.bits_per_sample = 24;
    format_.block_align = 6;
    format_.byte_rate = format_.sample_rate * 6;
    format_.total_frames = total_frames_;
}

bool WavFile::writeWavFile(const std::string& path,
                           const float* left, const float* right,
                           size_t num_frames,
                           uint32_t sample_rate,
                           int bits_per_sample) {
    WavWriter writer(path, sample_rate, 2, bits_per_sample);
    if (!writer.isOpen()) {
        return false;
    }
    if (!writer.writePlanar(left, right, num_frames)) {
        return false;
    }
    return writer.close();
}

} // namespace audio
} // namespace daw

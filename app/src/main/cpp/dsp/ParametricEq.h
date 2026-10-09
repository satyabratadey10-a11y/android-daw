#ifndef ANDROID_DAW_PARAMETRIC_EQ_H
#define ANDROID_DAW_PARAMETRIC_EQ_H

#include <array>
#include <cstddef>
#include "BiquadFilter.h"

namespace daw {
namespace dsp {

/**
 * 5-Band Parametric Equalizer.
 *
 * Cascades 5 BiquadFilter instances in Direct Form II Transposed structure.
 * Supports planar stereo in-place processing and composite curve evaluation.
 */
class ParametricEq {
public:
    static constexpr size_t kNumBands = 5;

    struct BandConfig {
        FilterType type;
        float frequency_hz;
        float gain_db;
        float q_factor;
    };

    explicit ParametricEq(float sample_rate = 48000.0f);
    ~ParametricEq() = default;

    void setSampleRate(float sample_rate) noexcept;

    void setBand(size_t band_index, FilterType type, float freq_hz, float gain_db, float q_factor) noexcept;
    void setBandGain(size_t band_index, float gain_db) noexcept;
    void setBandFrequency(size_t band_index, float freq_hz) noexcept;
    void setBandQ(size_t band_index, float q_factor) noexcept;

    [[nodiscard]] BandConfig getBandConfig(size_t band_index) const noexcept;
    [[nodiscard]] const BiquadFilter& getBandFilter(size_t band_index) const noexcept;

    void setEnabled(bool enabled) noexcept { enabled_ = enabled; }
    [[nodiscard]] bool isEnabled() const noexcept { return enabled_; }

    // In-place processing on mono buffer
    void process(float* buffer, size_t num_frames) noexcept;

    // In-place processing on planar stereo buffers
    void processStereo(float* left, float* right, size_t num_frames) noexcept;

    // Composite magnitude response (linear scale, 1.0 = 0 dB)
    [[nodiscard]] float getCompositeMagnitude(float frequency_hz) const noexcept;

    // Composite magnitude response in decibels
    [[nodiscard]] float getCompositeMagnitudeDb(float frequency_hz) const noexcept;

    void reset() noexcept;

private:
    float sample_rate_{48000.0f};
    bool enabled_{true};
    std::array<BiquadFilter, kNumBands> bands_;
};

} // namespace dsp
} // namespace daw

#endif // ANDROID_DAW_PARAMETRIC_EQ_H

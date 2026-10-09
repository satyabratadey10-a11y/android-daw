#ifndef ANDROID_DAW_BIQUAD_FILTER_H
#define ANDROID_DAW_BIQUAD_FILTER_H

#include <cmath>
#include <cstddef>
#include <algorithm>

namespace daw {
namespace dsp {

enum class FilterType : int {
    PEAKING = 0,
    LOW_SHELF = 1,
    HIGH_SHELF = 2,
    LOW_PASS = 3,
    HIGH_PASS = 4,
    BAND_PASS = 5,
    NOTCH = 6,
    BYPASS = 7
};

/**
 * Direct Form II Transposed (DF2T) Biquad Filter.
 *
 * Implements Robert Bristow-Johnson (RBJ) Audio EQ Cookbook formulas.
 * Supports planar stereo processing and anti-denormal handling.
 */
class BiquadFilter {
public:
    explicit BiquadFilter(float sample_rate = 48000.0f);
    ~BiquadFilter() = default;

    void setSampleRate(float sample_rate) noexcept;
    void setParameters(FilterType type, float frequency_hz, float gain_db, float q_factor) noexcept;

    [[nodiscard]] FilterType getType() const noexcept { return type_; }
    [[nodiscard]] float getFrequency() const noexcept { return frequency_hz_; }
    [[nodiscard]] float getGainDb() const noexcept { return gain_db_; }
    [[nodiscard]] float getQ() const noexcept { return q_factor_; }

    // Sample-by-sample processing
    inline float processSampleLeft(float input) noexcept {
        const float out = b0_ * input + s1_l_;
        s1_l_ = b1_ * input - a1_ * out + s2_l_;
        s2_l_ = b2_ * input - a2_ * out;
        sanitizeDenormals(s1_l_, s2_l_);
        return out;
    }

    inline float processSampleRight(float input) noexcept {
        const float out = b0_ * input + s1_r_;
        s1_r_ = b1_ * input - a1_ * out + s2_r_;
        s2_r_ = b2_ * input - a2_ * out;
        sanitizeDenormals(s1_r_, s2_r_);
        return out;
    }

    // Process mono buffer in-place
    void process(float* buffer, size_t num_frames) noexcept;

    // Process planar stereo buffers in-place
    void processStereo(float* left, float* right, size_t num_frames) noexcept;

    // Calculate magnitude response at a given frequency in Hz
    [[nodiscard]] float getMagnitudeResponse(float frequency_hz) const noexcept;

    void reset() noexcept;

private:
    void computeCoefficients() noexcept;

    static inline void sanitizeDenormals(float& s1, float& s2) noexcept {
        if (std::abs(s1) < 1.0e-25f) s1 = 0.0f;
        if (std::abs(s2) < 1.0e-25f) s2 = 0.0f;
    }

    FilterType type_{FilterType::PEAKING};
    float frequency_hz_{1000.0f};
    float gain_db_{0.0f};
    float q_factor_{0.7071f};
    float sample_rate_{48000.0f};

    // Normalized biquad coefficients (a0 is normalized to 1.0)
    float b0_{1.0f};
    float b1_{0.0f};
    float b2_{0.0f};
    float a1_{0.0f};
    float a2_{0.0f};

    // Direct Form II Transposed delay registers (2 states per channel)
    float s1_l_{0.0f};
    float s2_l_{0.0f};
    float s1_r_{0.0f};
    float s2_r_{0.0f};
};

} // namespace dsp
} // namespace daw

#endif // ANDROID_DAW_BIQUAD_FILTER_H

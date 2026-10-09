#ifndef ANDROID_DAW_PANNER_H
#define ANDROID_DAW_PANNER_H

#include <cmath>
#include <cstddef>
#include <algorithm>
#include "ParameterSmoother.h"

namespace daw {
namespace dsp {

/**
 * Panner: Constant-power stereo panner following sine/cosine law.
 *
 * theta = (pi / 4) * (pan + 1.0)
 * gain_l = cos(theta)
 * gain_r = sin(theta)
 * Conserves acoustic power: gain_l^2 + gain_r^2 == 1.0
 */
class Panner {
public:
    explicit Panner(float sample_rate = 48000.0f);
    ~Panner() = default;

    void setSampleRate(float sample_rate) noexcept;

    // Pan position in range [-1.0f (full left), +1.0f (full right)]
    void setPan(float pan) noexcept;
    [[nodiscard]] float getPan() const noexcept;

    // Static / pure mathematical gain calculator
    static void calculateGains(float pan, float& gain_l, float& gain_r) noexcept;
    // Lowercase alias for survey/test parity
    void calculate_gains(float pan, float& gain_l, float& gain_r) const noexcept;

    // In-place processing of planar stereo buffers
    void process(float* left, float* right, size_t num_frames) noexcept;

    // Out-of-place processing
    void process(const float* in_left, const float* in_right,
                 float* out_left, float* out_right,
                 size_t num_frames) noexcept;

    // Mono input to planar stereo output
    void processMonoToStereo(const float* in_mono,
                             float* out_left, float* out_right,
                             size_t num_frames) noexcept;

    void reset() noexcept;

private:
    ParameterSmoother pan_smoother_;
    float target_pan_{0.0f};
};

} // namespace dsp
} // namespace daw

#endif // ANDROID_DAW_PANNER_H

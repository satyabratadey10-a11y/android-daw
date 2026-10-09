#ifndef ANDROID_DAW_PARAMETER_SMOOTHER_H
#define ANDROID_DAW_PARAMETER_SMOOTHER_H

#include <cmath>
#include <cstddef>
#include <algorithm>

namespace daw {
namespace dsp {

/**
 * ParameterSmoother: Eliminates zipper noise on gain, pan, and filter controls.
 *
 * Implements:
 * 1. Sample-by-sample 1-pole IIR lowpass smoothing (fc ~ 20 Hz).
 * 2. Block-based linear ramping across render frames.
 * 3. Anti-denormal / snap-to-target threshold.
 */
class ParameterSmoother {
public:
    explicit ParameterSmoother(float initial_value = 1.0f, float sample_rate = 48000.0f, float cutoff_hz = 20.0f)
        : current_value_(initial_value),
          target_value_(initial_value),
          sample_rate_(sample_rate),
          cutoff_hz_(cutoff_hz) {
        updateCoefficient();
    }

    void setSampleRate(float sample_rate) noexcept {
        if (sample_rate > 0.0f) {
            sample_rate_ = sample_rate;
            updateCoefficient();
        }
    }

    void setCutoffHz(float cutoff_hz) noexcept {
        if (cutoff_hz > 0.0f) {
            cutoff_hz_ = cutoff_hz;
            updateCoefficient();
        }
    }

    void setTarget(float target) noexcept {
        target_value_ = target;
    }

    [[nodiscard]] float getTarget() const noexcept {
        return target_value_;
    }

    [[nodiscard]] float getCurrent() const noexcept {
        return current_value_;
    }

    void reset(float value) noexcept {
        current_value_ = value;
        target_value_ = value;
    }

    [[nodiscard]] bool isSmoothing() const noexcept {
        return std::abs(target_value_ - current_value_) > kSnapThreshold;
    }

    // Sample-by-sample 1-pole IIR step
    inline float nextValue() noexcept {
        if (std::abs(target_value_ - current_value_) <= kSnapThreshold) {
            current_value_ = target_value_;
            return current_value_;
        }
        current_value_ += alpha_ * (target_value_ - current_value_);
        return current_value_;
    }

    // Apply smoothed gain directly to a mono buffer in-place
    void applyGain(float* buffer, size_t num_frames) noexcept {
        if (!buffer || num_frames == 0) return;

        if (!isSmoothing()) {
            if (current_value_ == 1.0f) {
                return; // Unity gain optimization
            }
            if (current_value_ == 0.0f) {
                std::fill_n(buffer, num_frames, 0.0f);
                return;
            }
            for (size_t i = 0; i < num_frames; ++i) {
                buffer[i] *= current_value_;
            }
            return;
        }

        // Linear interpolation across the block for deterministic RT performance
        const float start = current_value_;
        // Step forward by 1-pole for block target
        const float end_estimate = start + (target_value_ - start) * (1.0f - std::pow(1.0f - alpha_, static_cast<float>(num_frames)));
        const float step = (end_estimate - start) / static_cast<float>(num_frames);

        float gain = start;
        for (size_t i = 0; i < num_frames; ++i) {
            buffer[i] *= gain;
            gain += step;
        }

        current_value_ = gain;
        if (std::abs(target_value_ - current_value_) <= kSnapThreshold) {
            current_value_ = target_value_;
        }
    }

    // Apply smoothed gain to planar stereo buffers in-place
    void applyGainStereo(float* left, float* right, size_t num_frames) noexcept {
        if (num_frames == 0) return;

        if (!isSmoothing()) {
            if (current_value_ == 1.0f) return;
            if (current_value_ == 0.0f) {
                if (left) std::fill_n(left, num_frames, 0.0f);
                if (right) std::fill_n(right, num_frames, 0.0f);
                return;
            }
            if (left) {
                for (size_t i = 0; i < num_frames; ++i) left[i] *= current_value_;
            }
            if (right) {
                for (size_t i = 0; i < num_frames; ++i) right[i] *= current_value_;
            }
            return;
        }

        const float start = current_value_;
        const float end_estimate = start + (target_value_ - start) * (1.0f - std::pow(1.0f - alpha_, static_cast<float>(num_frames)));
        const float step = (end_estimate - start) / static_cast<float>(num_frames);

        float gain = start;
        for (size_t i = 0; i < num_frames; ++i) {
            if (left) left[i] *= gain;
            if (right) right[i] *= gain;
            gain += step;
        }

        current_value_ = gain;
        if (std::abs(target_value_ - current_value_) <= kSnapThreshold) {
            current_value_ = target_value_;
        }
    }

private:
    void updateCoefficient() noexcept {
        constexpr float kTwoPi = 6.28318530717958647692f;
        const float w = kTwoPi * (cutoff_hz_ / sample_rate_);
        alpha_ = 1.0f - std::exp(-w);
        alpha_ = std::clamp(alpha_, 0.0001f, 1.0f);
    }

    static constexpr float kSnapThreshold = 1.0e-5f;

    float current_value_{1.0f};
    float target_value_{1.0f};
    float sample_rate_{48000.0f};
    float cutoff_hz_{20.0f};
    float alpha_{0.0026f};
};

} // namespace dsp
} // namespace daw

#endif // ANDROID_DAW_PARAMETER_SMOOTHER_H

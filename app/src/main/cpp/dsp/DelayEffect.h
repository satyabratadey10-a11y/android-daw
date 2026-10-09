#ifndef ANDROID_DAW_DELAY_EFFECT_H
#define ANDROID_DAW_DELAY_EFFECT_H

#include <vector>
#include <cstddef>
#include "ParameterSmoother.h"

namespace daw {
namespace dsp {

/**
 * Stereo Delay Effect with circular buffer, fractional interpolation,
 * 1-pole high-frequency damping filter, and feedback saturation.
 */
class DelayEffect {
public:
    explicit DelayEffect(float sample_rate = 48000.0f, float max_delay_seconds = 2.5f);
    ~DelayEffect() = default;

    void setSampleRate(float sample_rate) noexcept;

    // Delay time in milliseconds (clamped to [1.0f, max_delay_ms])
    void setDelayTimeMs(float delay_ms) noexcept;
    [[nodiscard]] float getDelayTimeMs() const noexcept { return delay_ms_; }

    // Feedback gain in range [0.0f, 0.95f]
    void setFeedback(float feedback) noexcept;
    [[nodiscard]] float getFeedback() const noexcept { return feedback_; }

    // Wet/dry mix in range [0.0f (pure dry), 1.0f (pure wet)]
    void setWetDry(float wet_dry) noexcept;
    [[nodiscard]] float getWetDry() const noexcept { return wet_dry_; }

    // High frequency damping coefficient [0.0f (no damping), 0.85f (heavy damping)]
    void setDamping(float damping) noexcept;
    [[nodiscard]] float getDamping() const noexcept { return damping_; }

    void setEnabled(bool enabled) noexcept { enabled_ = enabled; }
    [[nodiscard]] bool isEnabled() const noexcept { return enabled_; }

    // In-place processing of planar stereo buffers
    void processStereo(float* left, float* right, size_t num_frames) noexcept;

    // In-place processing of mono buffer
    void process(float* buffer, size_t num_frames) noexcept;

    void reset() noexcept;

private:
    float sample_rate_{48000.0f};
    float max_delay_seconds_{2.5f};
    bool enabled_{true};

    float delay_ms_{350.0f};
    float feedback_{0.35f};
    float wet_dry_{0.3f};
    float damping_{0.25f};

    // Smoothers for zipper-free modulation
    ParameterSmoother delay_smoother_;
    ParameterSmoother feedback_smoother_;
    ParameterSmoother wet_dry_smoother_;

    // Power-of-two circular buffers for Left and Right channels
    size_t buffer_capacity_{131072};
    size_t buffer_mask_{131071};
    std::vector<float> buffer_left_;
    std::vector<float> buffer_right_;
    size_t write_pos_{0};

    // Damping filter states (1-pole lowpass)
    float damp_state_left_{0.0f};
    float damp_state_right_{0.0f};
};

} // namespace dsp
} // namespace daw

#endif // ANDROID_DAW_DELAY_EFFECT_H

#include "DelayEffect.h"
#include <cmath>
#include <algorithm>

namespace daw {
namespace dsp {

DelayEffect::DelayEffect(float sample_rate, float max_delay_seconds)
    : sample_rate_(sample_rate > 0.0f ? sample_rate : 48000.0f),
      max_delay_seconds_(max_delay_seconds > 0.1f ? max_delay_seconds : 2.5f),
      delay_smoother_(350.0f, sample_rate_, 15.0f),
      feedback_smoother_(0.35f, sample_rate_, 20.0f),
      wet_dry_smoother_(0.3f, sample_rate_, 20.0f) {

    // Allocate power-of-two circular buffer
    const size_t min_capacity = static_cast<size_t>(sample_rate_ * max_delay_seconds_) + 1024;
    buffer_capacity_ = 1;
    while (buffer_capacity_ < min_capacity) {
        buffer_capacity_ <<= 1;
    }
    buffer_mask_ = buffer_capacity_ - 1;

    buffer_left_.assign(buffer_capacity_, 0.0f);
    buffer_right_.assign(buffer_capacity_, 0.0f);
    write_pos_ = 0;
}

void DelayEffect::setSampleRate(float sample_rate) noexcept {
    if (sample_rate <= 0.0f || sample_rate == sample_rate_) return;

    sample_rate_ = sample_rate;
    delay_smoother_.setSampleRate(sample_rate_);
    feedback_smoother_.setSampleRate(sample_rate_);
    wet_dry_smoother_.setSampleRate(sample_rate_);

    const size_t min_capacity = static_cast<size_t>(sample_rate_ * max_delay_seconds_) + 1024;
    size_t new_cap = 1;
    while (new_cap < min_capacity) {
        new_cap <<= 1;
    }
    if (new_cap != buffer_capacity_) {
        buffer_capacity_ = new_cap;
        buffer_mask_ = buffer_capacity_ - 1;
        buffer_left_.assign(buffer_capacity_, 0.0f);
        buffer_right_.assign(buffer_capacity_, 0.0f);
        write_pos_ = 0;
    }
}

void DelayEffect::setDelayTimeMs(float delay_ms) noexcept {
    const float max_ms = max_delay_seconds_ * 1000.0f;
    delay_ms_ = std::clamp(delay_ms, 1.0f, max_ms);
    delay_smoother_.setTarget(delay_ms_);
}

void DelayEffect::setFeedback(float feedback) noexcept {
    feedback_ = std::clamp(feedback, 0.0f, 0.95f);
    feedback_smoother_.setTarget(feedback_);
}

void DelayEffect::setWetDry(float wet_dry) noexcept {
    wet_dry_ = std::clamp(wet_dry, 0.0f, 1.0f);
    wet_dry_smoother_.setTarget(wet_dry_);
}

void DelayEffect::setDamping(float damping) noexcept {
    damping_ = std::clamp(damping, 0.0f, 0.85f);
}

void DelayEffect::processStereo(float* left, float* right, size_t num_frames) noexcept {
    if (!enabled_ || num_frames == 0) return;

    const float damp = damping_;

    for (size_t i = 0; i < num_frames; ++i) {
        const float cur_delay_ms = delay_smoother_.nextValue();
        const float cur_feedback = feedback_smoother_.nextValue();
        const float cur_wet_dry = wet_dry_smoother_.nextValue();
        const float dry_gain = 1.0f - cur_wet_dry;
        const float wet_gain = cur_wet_dry;

        // Calculate fractional delay samples
        const float delay_samples = (cur_delay_ms * 0.001f) * sample_rate_;
        const auto delay_int = static_cast<size_t>(delay_samples);
        const float frac = delay_samples - static_cast<float>(delay_int);

        // Read positions with wrapping
        const size_t read_idx0 = (write_pos_ + buffer_capacity_ - delay_int) & buffer_mask_;
        const size_t read_idx1 = (read_idx0 + buffer_capacity_ - 1) & buffer_mask_;

        // Fractional interpolation on Left channel
        const float del_l0 = buffer_left_[read_idx0];
        const float del_l1 = buffer_left_[read_idx1];
        const float delayed_l = del_l0 + frac * (del_l1 - del_l0);

        // Fractional interpolation on Right channel
        const float del_r0 = buffer_right_[read_idx0];
        const float del_r1 = buffer_right_[read_idx1];
        const float delayed_r = del_r0 + frac * (del_r1 - del_r0);

        // 1-pole damping filter in feedback loop
        damp_state_left_ = (1.0f - damp) * delayed_l + damp * damp_state_left_;
        damp_state_right_ = (1.0f - damp) * delayed_r + damp * damp_state_right_;

        // Anti-denormal on damping states
        if (std::abs(damp_state_left_) < 1.0e-25f) damp_state_left_ = 0.0f;
        if (std::abs(damp_state_right_) < 1.0e-25f) damp_state_right_ = 0.0f;

        const float in_l = left ? left[i] : 0.0f;
        const float in_r = right ? right[i] : 0.0f;

        // Soft saturation in feedback to prevent harsh digital clipping
        float fb_sample_l = damp_state_left_ * cur_feedback;
        float fb_sample_r = damp_state_right_ * cur_feedback;
        if (fb_sample_l > 1.0f) fb_sample_l = 1.0f; else if (fb_sample_l < -1.0f) fb_sample_l = -1.0f;
        if (fb_sample_r > 1.0f) fb_sample_r = 1.0f; else if (fb_sample_r < -1.0f) fb_sample_r = -1.0f;

        // Write input + feedback into circular buffer
        buffer_left_[write_pos_] = in_l + fb_sample_l;
        buffer_right_[write_pos_] = in_r + fb_sample_r;

        // Output mix
        if (left) left[i] = in_l * dry_gain + delayed_l * wet_gain;
        if (right) right[i] = in_r * dry_gain + delayed_r * wet_gain;

        write_pos_ = (write_pos_ + 1) & buffer_mask_;
    }
}

void DelayEffect::process(float* buffer, size_t num_frames) noexcept {
    processStereo(buffer, buffer, num_frames);
}

void DelayEffect::reset() noexcept {
    std::fill(buffer_left_.begin(), buffer_left_.end(), 0.0f);
    std::fill(buffer_right_.begin(), buffer_right_.end(), 0.0f);
    write_pos_ = 0;
    damp_state_left_ = 0.0f;
    damp_state_right_ = 0.0f;
    delay_smoother_.reset(delay_ms_);
    feedback_smoother_.reset(feedback_);
    wet_dry_smoother_.reset(wet_dry_);
}

} // namespace dsp
} // namespace daw

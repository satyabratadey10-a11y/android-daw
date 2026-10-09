#include "MasterLimiter.h"

namespace daw {
namespace dsp {

MasterLimiter::MasterLimiter(float sample_rate, float lookahead_ms)
    : sample_rate_(sample_rate > 0.0f ? sample_rate : 48000.0f),
      lookahead_ms_(lookahead_ms > 0.0f ? lookahead_ms : 5.0f) {
    
    // Calculate required lookahead sample count
    lookahead_samples_ = static_cast<size_t>(std::ceil(sample_rate_ * (lookahead_ms_ / 1000.0f)));
    if (lookahead_samples_ < 1) {
        lookahead_samples_ = 1;
    }

    // Allocate power-of-two capacity for circular delay buffers
    delay_buf_capacity_ = 1;
    while (delay_buf_capacity_ < (lookahead_samples_ + 256)) {
        delay_buf_capacity_ <<= 1;
    }
    delay_buf_mask_ = delay_buf_capacity_ - 1;

    delay_buf_l_.assign(delay_buf_capacity_, 0.0f);
    delay_buf_r_.assign(delay_buf_capacity_, 0.0f);
    delay_write_pos_ = 0;

    // Linear thresholds
    threshold_linear_ = std::pow(10.0f, threshold_db_ / 20.0f);
    ceiling_linear_ = std::pow(10.0f, ceiling_db_ / 20.0f);

    updateBallistics();
    reset();
}

void MasterLimiter::setSampleRate(float sample_rate) noexcept {
    if (sample_rate <= 0.0f || std::abs(sample_rate - sample_rate_) < 0.1f) {
        return;
    }
    sample_rate_ = sample_rate;

    lookahead_samples_ = static_cast<size_t>(std::ceil(sample_rate_ * (lookahead_ms_ / 1000.0f)));
    if (lookahead_samples_ < 1) {
        lookahead_samples_ = 1;
    }

    size_t new_capacity = 1;
    while (new_capacity < (lookahead_samples_ + 256)) {
        new_capacity <<= 1;
    }

    if (new_capacity != delay_buf_capacity_) {
        delay_buf_capacity_ = new_capacity;
        delay_buf_mask_ = delay_buf_capacity_ - 1;
        delay_buf_l_.assign(delay_buf_capacity_, 0.0f);
        delay_buf_r_.assign(delay_buf_capacity_, 0.0f);
        delay_write_pos_ = 0;
    }

    updateBallistics();
}

void MasterLimiter::setThresholdDb(float threshold_db) noexcept {
    threshold_db_ = std::clamp(threshold_db, -60.0f, 0.0f);
    threshold_linear_ = std::pow(10.0f, threshold_db_ / 20.0f);
}

void MasterLimiter::setCeilingDb(float ceiling_db) noexcept {
    ceiling_db_ = std::clamp(ceiling_db, -24.0f, 0.0f);
    ceiling_linear_ = std::pow(10.0f, ceiling_db_ / 20.0f);
}

void MasterLimiter::setAttackMs(float attack_ms) noexcept {
    attack_ms_ = std::clamp(attack_ms, 0.05f, 50.0f);
    updateBallistics();
}

void MasterLimiter::setReleaseMs(float release_ms) noexcept {
    release_ms_ = std::clamp(release_ms, 1.0f, 1000.0f);
    updateBallistics();
}

void MasterLimiter::updateBallistics() noexcept {
    // 1-pole ballistics coefficients
    const float att_sec = (attack_ms_ * 0.001f) * sample_rate_;
    const float rel_sec = (release_ms_ * 0.001f) * sample_rate_;

    attack_coeff_ = std::exp(-1.0f / std::max(att_sec, 1.0f));
    release_coeff_ = std::exp(-1.0f / std::max(rel_sec, 1.0f));
}

float MasterLimiter::softClipCubic(float x, float ceiling) noexcept {
    const float abs_x = std::abs(x);
    const float knee_start = 0.6666667f * ceiling;

    if (abs_x <= knee_start) {
        return x;
    }
    if (abs_x >= ceiling) {
        return (x > 0.0f) ? ceiling : -ceiling;
    }

    // Normalized variable t in [0.0, 1.0] across transition knee
    const float knee_width = ceiling - knee_start;
    const float t = (abs_x - knee_start) / knee_width;

    // Hermite cubic spline: C1 continuous, 0 overshoot
    // k(t) = t + t^2 - t^3
    const float curve = t + (t * t) - (t * t * t);
    const float y = knee_start + knee_width * curve;

    return (x > 0.0f) ? y : -y;
}

void MasterLimiter::processStereo(float* left, float* right, size_t num_frames) noexcept {
    if (!enabled_ || num_frames == 0) {
        return;
    }
    if (!left && !right) {
        return;
    }

    for (size_t i = 0; i < num_frames; ++i) {
        const float in_l = left ? left[i] : 0.0f;
        const float in_r = right ? right[i] : 0.0f;

        // 1. Peak detection on incoming non-delayed audio
        const float peak = std::max(std::abs(in_l), std::abs(in_r));

        // 2. Dual envelope follower ballistics
        if (peak > envelope_) {
            envelope_ = attack_coeff_ * envelope_ + (1.0f - attack_coeff_) * peak;
        } else {
            envelope_ = release_coeff_ * envelope_ + (1.0f - release_coeff_) * peak;
        }

        // Anti-denormal protection
        if (envelope_ < 1.0e-25f) {
            envelope_ = 0.0f;
        }

        // 3. Dynamic gain computer enforcing threshold
        float target_gain = 1.0f;
        if (envelope_ > threshold_linear_) {
            target_gain = threshold_linear_ / envelope_;
        }
        current_gain_ = target_gain;

        // 4. Retrieve delayed sample from lookahead circular buffer
        const size_t read_pos = (delay_write_pos_ + delay_buf_capacity_ - lookahead_samples_) & delay_buf_mask_;
        const float delayed_l = delay_buf_l_[read_pos];
        const float delayed_r = delay_buf_r_[read_pos];

        // Store incoming sample into circular delay buffer
        delay_buf_l_[delay_write_pos_] = in_l;
        delay_buf_r_[delay_write_pos_] = in_r;
        delay_write_pos_ = (delay_write_pos_ + 1) & delay_buf_mask_;

        // 5. Apply gain reduction and ceiling scaling
        float out_l = delayed_l * current_gain_;
        float out_r = delayed_r * current_gain_;

        // 6. Cubic soft-knee saturation guaranteeing zero ceiling overshoot
        out_l = softClipCubic(out_l, ceiling_linear_);
        out_r = softClipCubic(out_r, ceiling_linear_);

        if (left) left[i] = out_l;
        if (right) right[i] = out_r;
    }
}

void MasterLimiter::process(const float* input, float* output, size_t num_frames) noexcept {
    if (!input || !output || num_frames == 0) return;

    if (!enabled_) {
        std::copy_n(input, num_frames, output);
        return;
    }

    for (size_t i = 0; i < num_frames; ++i) {
        const float in_sample = input[i];
        const float peak = std::abs(in_sample);

        if (peak > envelope_) {
            envelope_ = attack_coeff_ * envelope_ + (1.0f - attack_coeff_) * peak;
        } else {
            envelope_ = release_coeff_ * envelope_ + (1.0f - release_coeff_) * peak;
        }
        if (envelope_ < 1.0e-25f) envelope_ = 0.0f;

        float target_gain = 1.0f;
        if (envelope_ > threshold_linear_) {
            target_gain = threshold_linear_ / envelope_;
        }
        current_gain_ = target_gain;

        const size_t read_pos = (delay_write_pos_ + delay_buf_capacity_ - lookahead_samples_) & delay_buf_mask_;
        const float delayed_sample = delay_buf_l_[read_pos];

        delay_buf_l_[delay_write_pos_] = in_sample;
        delay_buf_r_[delay_write_pos_] = in_sample;
        delay_write_pos_ = (delay_write_pos_ + 1) & delay_buf_mask_;

        float out = delayed_sample * current_gain_;
        out = softClipCubic(out, ceiling_linear_);
        output[i] = out;
    }
}

void MasterLimiter::process(const float* in_left, const float* in_right,
                            float* out_left, float* out_right,
                            size_t num_frames) noexcept {
    if (num_frames == 0) return;

    if (!enabled_) {
        if (in_left && out_left) std::copy_n(in_left, num_frames, out_left);
        if (in_right && out_right) std::copy_n(in_right, num_frames, out_right);
        return;
    }

    for (size_t i = 0; i < num_frames; ++i) {
        const float in_l = in_left ? in_left[i] : 0.0f;
        const float in_r = in_right ? in_right[i] : 0.0f;

        const float peak = std::max(std::abs(in_l), std::abs(in_r));

        if (peak > envelope_) {
            envelope_ = attack_coeff_ * envelope_ + (1.0f - attack_coeff_) * peak;
        } else {
            envelope_ = release_coeff_ * envelope_ + (1.0f - release_coeff_) * peak;
        }
        if (envelope_ < 1.0e-25f) envelope_ = 0.0f;

        float target_gain = 1.0f;
        if (envelope_ > threshold_linear_) {
            target_gain = threshold_linear_ / envelope_;
        }
        current_gain_ = target_gain;

        const size_t read_pos = (delay_write_pos_ + delay_buf_capacity_ - lookahead_samples_) & delay_buf_mask_;
        const float delayed_l = delay_buf_l_[read_pos];
        const float delayed_r = delay_buf_r_[read_pos];

        delay_buf_l_[delay_write_pos_] = in_l;
        delay_buf_r_[delay_write_pos_] = in_r;
        delay_write_pos_ = (delay_write_pos_ + 1) & delay_buf_mask_;

        float out_l_val = delayed_l * current_gain_;
        float out_r_val = delayed_r * current_gain_;

        out_l_val = softClipCubic(out_l_val, ceiling_linear_);
        out_r_val = softClipCubic(out_r_val, ceiling_linear_);

        if (out_left) out_left[i] = out_l_val;
        if (out_right) out_right[i] = out_r_val;
    }
}

float MasterLimiter::getCurrentGainReductionDb() const noexcept {
    if (current_gain_ >= 0.99999f) {
        return 0.0f;
    }
    return 20.0f * std::log10(std::max(current_gain_, 1.0e-5f));
}

void MasterLimiter::reset() noexcept {
    std::fill(delay_buf_l_.begin(), delay_buf_l_.end(), 0.0f);
    std::fill(delay_buf_r_.begin(), delay_buf_r_.end(), 0.0f);
    delay_write_pos_ = 0;
    envelope_ = 0.0f;
    current_gain_ = 1.0f;
}

} // namespace dsp
} // namespace daw

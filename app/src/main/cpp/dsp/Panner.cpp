#include "Panner.h"

namespace daw {
namespace dsp {

constexpr float kPi = 3.14159265358979323846f;
constexpr float kPiOver4 = kPi / 4.0f;

Panner::Panner(float sample_rate)
    : pan_smoother_(0.0f, sample_rate, 25.0f),
      target_pan_(0.0f) {
}

void Panner::setSampleRate(float sample_rate) noexcept {
    pan_smoother_.setSampleRate(sample_rate);
}

void Panner::setPan(float pan) noexcept {
    target_pan_ = std::clamp(pan, -1.0f, 1.0f);
    pan_smoother_.setTarget(target_pan_);
}

float Panner::getPan() const noexcept {
    return target_pan_;
}

void Panner::calculateGains(float pan, float& gain_l, float& gain_r) noexcept {
    const float clamped_pan = std::clamp(pan, -1.0f, 1.0f);
    // theta = (pi / 4) * (pan + 1.0) -> [0, pi/2]
    const float theta = kPiOver4 * (clamped_pan + 1.0f);
    gain_l = std::cos(theta);
    gain_r = std::sin(theta);
}

void Panner::calculate_gains(float pan, float& gain_l, float& gain_r) const noexcept {
    calculateGains(pan, gain_l, gain_r);
}

void Panner::process(float* left, float* right, size_t num_frames) noexcept {
    if (num_frames == 0) return;

    if (!pan_smoother_.isSmoothing()) {
        float gl = 0.0f, gr = 0.0f;
        calculateGains(pan_smoother_.getCurrent(), gl, gr);

        // Normalize by sqrt(2) so center is unity (0dB) gain on each channel
        constexpr float kNorm = 1.41421356237309504880f; // sqrt(2)
        const float scaled_gl = gl * kNorm;
        const float scaled_gr = gr * kNorm;

        if (left) {
            for (size_t i = 0; i < num_frames; ++i) left[i] *= scaled_gl;
        }
        if (right) {
            for (size_t i = 0; i < num_frames; ++i) right[i] *= scaled_gr;
        }
        return;
    }

    constexpr float kNorm = 1.41421356237309504880f;
    for (size_t i = 0; i < num_frames; ++i) {
        const float p = pan_smoother_.nextValue();
        float gl = 0.0f, gr = 0.0f;
        calculateGains(p, gl, gr);
        if (left) left[i] *= (gl * kNorm);
        if (right) right[i] *= (gr * kNorm);
    }
}

void Panner::process(const float* in_left, const float* in_right,
                     float* out_left, float* out_right,
                     size_t num_frames) noexcept {
    if (num_frames == 0) return;

    constexpr float kNorm = 1.41421356237309504880f;
    if (!pan_smoother_.isSmoothing()) {
        float gl = 0.0f, gr = 0.0f;
        calculateGains(pan_smoother_.getCurrent(), gl, gr);
        const float scaled_gl = gl * kNorm;
        const float scaled_gr = gr * kNorm;

        for (size_t i = 0; i < num_frames; ++i) {
            out_left[i] = (in_left ? in_left[i] : 0.0f) * scaled_gl;
            out_right[i] = (in_right ? in_right[i] : 0.0f) * scaled_gr;
        }
        return;
    }

    for (size_t i = 0; i < num_frames; ++i) {
        const float p = pan_smoother_.nextValue();
        float gl = 0.0f, gr = 0.0f;
        calculateGains(p, gl, gr);
        out_left[i] = (in_left ? in_left[i] : 0.0f) * (gl * kNorm);
        out_right[i] = (in_right ? in_right[i] : 0.0f) * (gr * kNorm);
    }
}

void Panner::processMonoToStereo(const float* in_mono,
                                float* out_left, float* out_right,
                                size_t num_frames) noexcept {
    if (!in_mono || num_frames == 0) return;

    if (!pan_smoother_.isSmoothing()) {
        float gl = 0.0f, gr = 0.0f;
        calculateGains(pan_smoother_.getCurrent(), gl, gr);

        for (size_t i = 0; i < num_frames; ++i) {
            out_left[i] = in_mono[i] * gl;
            out_right[i] = in_mono[i] * gr;
        }
        return;
    }

    for (size_t i = 0; i < num_frames; ++i) {
        const float p = pan_smoother_.nextValue();
        float gl = 0.0f, gr = 0.0f;
        calculateGains(p, gl, gr);
        out_left[i] = in_mono[i] * gl;
        out_right[i] = in_mono[i] * gr;
    }
}

void Panner::reset() noexcept {
    pan_smoother_.reset(target_pan_);
}

} // namespace dsp
} // namespace daw

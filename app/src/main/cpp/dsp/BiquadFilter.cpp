#include "BiquadFilter.h"
#include <complex>

namespace daw {
namespace dsp {

constexpr float kPi = 3.14159265358979323846f;

BiquadFilter::BiquadFilter(float sample_rate)
    : sample_rate_(sample_rate > 0.0f ? sample_rate : 48000.0f) {
    computeCoefficients();
}

void BiquadFilter::setSampleRate(float sample_rate) noexcept {
    if (sample_rate > 0.0f && sample_rate != sample_rate_) {
        sample_rate_ = sample_rate;
        computeCoefficients();
    }
}

void BiquadFilter::setParameters(FilterType type, float frequency_hz, float gain_db, float q_factor) noexcept {
    type_ = type;
    // Constrain frequency to valid range [10 Hz, 0.495 * Nyquist]
    const float nyquist = sample_rate_ * 0.495f;
    frequency_hz_ = std::clamp(frequency_hz, 10.0f, nyquist);
    gain_db_ = std::clamp(gain_db, -48.0f, 48.0f);
    q_factor_ = std::clamp(q_factor, 0.05f, 30.0f);

    computeCoefficients();
}

void BiquadFilter::computeCoefficients() noexcept {
    if (type_ == FilterType::BYPASS) {
        b0_ = 1.0f;
        b1_ = 0.0f;
        b2_ = 0.0f;
        a1_ = 0.0f;
        a2_ = 0.0f;
        return;
    }

    const float omega = (2.0f * kPi * frequency_hz_) / sample_rate_;
    const float sin_w = std::sin(omega);
    const float cos_w = std::cos(omega);
    const float alpha = sin_w / (2.0f * q_factor_);
    const float A = std::pow(10.0f, gain_db_ / 40.0f); // sqrt of linear gain

    float b0 = 1.0f, b1 = 0.0f, b2 = 0.0f;
    float a0 = 1.0f, a1 = 0.0f, a2 = 0.0f;

    switch (type_) {
        case FilterType::PEAKING: {
            b0 = 1.0f + alpha * A;
            b1 = -2.0f * cos_w;
            b2 = 1.0f - alpha * A;
            a0 = 1.0f + alpha / A;
            a1 = -2.0f * cos_w;
            a2 = 1.0f - alpha / A;
            break;
        }
        case FilterType::LOW_SHELF: {
            const float two_sqrt_A_alpha = 2.0f * std::sqrt(A) * alpha;
            b0 = A * ((A + 1.0f) - (A - 1.0f) * cos_w + two_sqrt_A_alpha);
            b1 = 2.0f * A * ((A - 1.0f) - (A + 1.0f) * cos_w);
            b2 = A * ((A + 1.0f) - (A - 1.0f) * cos_w - two_sqrt_A_alpha);
            a0 = (A + 1.0f) + (A - 1.0f) * cos_w + two_sqrt_A_alpha;
            a1 = -2.0f * ((A - 1.0f) + (A + 1.0f) * cos_w);
            a2 = (A + 1.0f) + (A - 1.0f) * cos_w - two_sqrt_A_alpha;
            break;
        }
        case FilterType::HIGH_SHELF: {
            const float two_sqrt_A_alpha = 2.0f * std::sqrt(A) * alpha;
            b0 = A * ((A + 1.0f) + (A - 1.0f) * cos_w + two_sqrt_A_alpha);
            b1 = -2.0f * A * ((A - 1.0f) + (A + 1.0f) * cos_w);
            b2 = A * ((A + 1.0f) + (A - 1.0f) * cos_w - two_sqrt_A_alpha);
            a0 = (A + 1.0f) - (A - 1.0f) * cos_w + two_sqrt_A_alpha;
            a1 = 2.0f * ((A - 1.0f) - (A + 1.0f) * cos_w);
            a2 = (A + 1.0f) - (A - 1.0f) * cos_w - two_sqrt_A_alpha;
            break;
        }
        case FilterType::LOW_PASS: {
            b0 = (1.0f - cos_w) * 0.5f;
            b1 = 1.0f - cos_w;
            b2 = (1.0f - cos_w) * 0.5f;
            a0 = 1.0f + alpha;
            a1 = -2.0f * cos_w;
            a2 = 1.0f - alpha;
            break;
        }
        case FilterType::HIGH_PASS: {
            b0 = (1.0f + cos_w) * 0.5f;
            b1 = -(1.0f + cos_w);
            b2 = (1.0f + cos_w) * 0.5f;
            a0 = 1.0f + alpha;
            a1 = -2.0f * cos_w;
            a2 = 1.0f - alpha;
            break;
        }
        case FilterType::BAND_PASS: {
            b0 = alpha;
            b1 = 0.0f;
            b2 = -alpha;
            a0 = 1.0f + alpha;
            a1 = -2.0f * cos_w;
            a2 = 1.0f - alpha;
            break;
        }
        case FilterType::NOTCH: {
            b0 = 1.0f;
            b1 = -2.0f * cos_w;
            b2 = 1.0f;
            a0 = 1.0f + alpha;
            a1 = -2.0f * cos_w;
            a2 = 1.0f - alpha;
            break;
        }
        case FilterType::BYPASS:
        default: {
            b0 = 1.0f;
            b1 = 0.0f;
            b2 = 0.0f;
            a0 = 1.0f;
            a1 = 0.0f;
            a2 = 0.0f;
            break;
        }
    }

    // Normalize coefficients by dividing by a0
    const float inv_a0 = (std::abs(a0) > 1.0e-9f) ? (1.0f / a0) : 1.0f;
    b0_ = b0 * inv_a0;
    b1_ = b1 * inv_a0;
    b2_ = b2 * inv_a0;
    a1_ = a1 * inv_a0;
    a2_ = a2 * inv_a0;
}

void BiquadFilter::process(float* buffer, size_t num_frames) noexcept {
    if (!buffer || num_frames == 0 || type_ == FilterType::BYPASS) return;

    for (size_t i = 0; i < num_frames; ++i) {
        const float in = buffer[i];
        const float out = b0_ * in + s1_l_;
        s1_l_ = b1_ * in - a1_ * out + s2_l_;
        s2_l_ = b2_ * in - a2_ * out;
        sanitizeDenormals(s1_l_, s2_l_);
        buffer[i] = out;
    }
}

void BiquadFilter::processStereo(float* left, float* right, size_t num_frames) noexcept {
    if (num_frames == 0 || type_ == FilterType::BYPASS) return;

    if (left && right) {
        for (size_t i = 0; i < num_frames; ++i) {
            const float in_l = left[i];
            const float out_l = b0_ * in_l + s1_l_;
            s1_l_ = b1_ * in_l - a1_ * out_l + s2_l_;
            s2_l_ = b2_ * in_l - a2_ * out_l;

            const float in_r = right[i];
            const float out_r = b0_ * in_r + s1_r_;
            s1_r_ = b1_ * in_r - a1_ * out_r + s2_r_;
            s2_r_ = b2_ * in_r - a2_ * out_r;

            sanitizeDenormals(s1_l_, s2_l_);
            sanitizeDenormals(s1_r_, s2_r_);

            left[i] = out_l;
            right[i] = out_r;
        }
    } else if (left) {
        process(left, num_frames);
    } else if (right) {
        for (size_t i = 0; i < num_frames; ++i) {
            const float in = right[i];
            const float out = b0_ * in + s1_r_;
            s1_r_ = b1_ * in - a1_ * out + s2_r_;
            s2_r_ = b2_ * in - a2_ * out;
            sanitizeDenormals(s1_r_, s2_r_);
            right[i] = out;
        }
    }
}

float BiquadFilter::getMagnitudeResponse(float frequency_hz) const noexcept {
    if (type_ == FilterType::BYPASS) return 1.0f;

    const float omega = (2.0f * kPi * frequency_hz) / sample_rate_;
    const std::complex<double> z_inv = std::exp(std::complex<double>(0.0, -static_cast<double>(omega)));
    const std::complex<double> z_inv2 = z_inv * z_inv;

    const std::complex<double> num = static_cast<double>(b0_) +
                                    static_cast<double>(b1_) * z_inv +
                                    static_cast<double>(b2_) * z_inv2;
    const std::complex<double> den = 1.0 +
                                    static_cast<double>(a1_) * z_inv +
                                    static_cast<double>(a2_) * z_inv2;

    const double mag = std::abs(num / den);
    return static_cast<float>(mag);
}

void BiquadFilter::reset() noexcept {
    s1_l_ = 0.0f;
    s2_l_ = 0.0f;
    s1_r_ = 0.0f;
    s2_r_ = 0.0f;
}

} // namespace dsp
} // namespace daw

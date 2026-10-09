#include "ParametricEq.h"

namespace daw {
namespace dsp {

ParametricEq::ParametricEq(float sample_rate)
    : sample_rate_(sample_rate > 0.0f ? sample_rate : 48000.0f) {
    for (auto& band : bands_) {
        band.setSampleRate(sample_rate_);
    }

    // Standard 5-band DAW default setup:
    // Band 0: Low Shelf @ 80 Hz, 0 dB, Q=0.7071
    // Band 1: Peaking @ 250 Hz, 0 dB, Q=1.0
    // Band 2: Peaking @ 1000 Hz, 0 dB, Q=1.0
    // Band 3: Peaking @ 4000 Hz, 0 dB, Q=1.0
    // Band 4: High Shelf @ 12000 Hz, 0 dB, Q=0.7071
    bands_[0].setParameters(FilterType::LOW_SHELF, 80.0f, 0.0f, 0.7071f);
    bands_[1].setParameters(FilterType::PEAKING, 250.0f, 0.0f, 1.0f);
    bands_[2].setParameters(FilterType::PEAKING, 1000.0f, 0.0f, 1.0f);
    bands_[3].setParameters(FilterType::PEAKING, 4000.0f, 0.0f, 1.0f);
    bands_[4].setParameters(FilterType::HIGH_SHELF, 12000.0f, 0.0f, 0.7071f);
}

void ParametricEq::setSampleRate(float sample_rate) noexcept {
    if (sample_rate > 0.0f && sample_rate != sample_rate_) {
        sample_rate_ = sample_rate;
        for (auto& band : bands_) {
            band.setSampleRate(sample_rate_);
        }
    }
}

void ParametricEq::setBand(size_t band_index, FilterType type, float freq_hz, float gain_db, float q_factor) noexcept {
    if (band_index < kNumBands) {
        bands_[band_index].setParameters(type, freq_hz, gain_db, q_factor);
    }
}

void ParametricEq::setBandGain(size_t band_index, float gain_db) noexcept {
    if (band_index < kNumBands) {
        auto& b = bands_[band_index];
        b.setParameters(b.getType(), b.getFrequency(), gain_db, b.getQ());
    }
}

void ParametricEq::setBandFrequency(size_t band_index, float freq_hz) noexcept {
    if (band_index < kNumBands) {
        auto& b = bands_[band_index];
        b.setParameters(b.getType(), freq_hz, b.getGainDb(), b.getQ());
    }
}

void ParametricEq::setBandQ(size_t band_index, float q_factor) noexcept {
    if (band_index < kNumBands) {
        auto& b = bands_[band_index];
        b.setParameters(b.getType(), b.getFrequency(), b.getGainDb(), q_factor);
    }
}

ParametricEq::BandConfig ParametricEq::getBandConfig(size_t band_index) const noexcept {
    if (band_index < kNumBands) {
        const auto& b = bands_[band_index];
        return {b.getType(), b.getFrequency(), b.getGainDb(), b.getQ()};
    }
    return {FilterType::BYPASS, 1000.0f, 0.0f, 1.0f};
}

const BiquadFilter& ParametricEq::getBandFilter(size_t band_index) const noexcept {
    if (band_index < kNumBands) {
        return bands_[band_index];
    }
    return bands_[0];
}

void ParametricEq::process(float* buffer, size_t num_frames) noexcept {
    if (!enabled_ || !buffer || num_frames == 0) return;

    for (auto& band : bands_) {
        band.process(buffer, num_frames);
    }
}

void ParametricEq::processStereo(float* left, float* right, size_t num_frames) noexcept {
    if (!enabled_ || num_frames == 0) return;

    for (auto& band : bands_) {
        band.processStereo(left, right, num_frames);
    }
}

float ParametricEq::getCompositeMagnitude(float frequency_hz) const noexcept {
    if (!enabled_) return 1.0f;

    float composite = 1.0f;
    for (const auto& band : bands_) {
        composite *= band.getMagnitudeResponse(frequency_hz);
    }
    return composite;
}

float ParametricEq::getCompositeMagnitudeDb(float frequency_hz) const noexcept {
    const float linear = getCompositeMagnitude(frequency_hz);
    if (linear <= 1.0e-5f) return -100.0f;
    return 20.0f * std::log10(linear);
}

void ParametricEq::reset() noexcept {
    for (auto& band : bands_) {
        band.reset();
    }
}

} // namespace dsp
} // namespace daw

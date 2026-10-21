#include "DrumKitSynth.h"
#include <cmath>
#include <algorithm>

namespace daw {
namespace dsp {

DrumKitSynth::DrumKitSynth(float sample_rate)
    : sample_rate_(sample_rate > 0.0f ? sample_rate : 48000.0f) {
    for (size_t i = 0; i < kPadCount; ++i) {
        voices_[i].type = static_cast<DrumPadType>(i);
        voices_[i].filter.setSampleRate(sample_rate_);
    }
}

void DrumKitSynth::setSampleRate(float sample_rate) noexcept {
    if (sample_rate <= 0.0f) return;
    sample_rate_ = sample_rate;
    for (auto& voice : voices_) {
        voice.filter.setSampleRate(sample_rate_);
    }
}

void DrumKitSynth::reset() noexcept {
    for (auto& voice : voices_) {
        voice.active = false;
        voice.env_level = 0.0f;
        voice.filter.reset();
    }
}

int DrumKitSynth::noteToPadIndex(int note_number) const noexcept {
    switch (note_number) {
        case 35: return static_cast<int>(DrumPadType::SUB_808);
        case 36: return static_cast<int>(DrumPadType::KICK);
        case 37: return static_cast<int>(DrumPadType::RIMSHOT);
        case 38:
        case 40: return static_cast<int>(DrumPadType::SNARE);
        case 39: return static_cast<int>(DrumPadType::CLAP);
        case 42:
        case 44: return static_cast<int>(DrumPadType::CLOSED_HAT);
        case 46: return static_cast<int>(DrumPadType::OPEN_HAT);
        case 41:
        case 43: return static_cast<int>(DrumPadType::LOW_TOM);
        case 45:
        case 47: return static_cast<int>(DrumPadType::MID_TOM);
        case 48:
        case 50: return static_cast<int>(DrumPadType::HIGH_TOM);
        case 49:
        case 57: return static_cast<int>(DrumPadType::CRASH);
        case 51:
        case 59: return static_cast<int>(DrumPadType::RIDE);
        case 54: return static_cast<int>(DrumPadType::TAMBOURINE);
        case 56: return static_cast<int>(DrumPadType::COWBELL);
        case 75: return static_cast<int>(DrumPadType::CLAVES);
        case 70: return static_cast<int>(DrumPadType::SHAKER);
        default:
            return std::abs(note_number) % static_cast<int>(kPadCount);
    }
}

void DrumKitSynth::noteOn(int note_number, float velocity) noexcept {
    if (velocity <= 0.0f) {
        noteOff(note_number);
        return;
    }
    const int pad_idx = noteToPadIndex(note_number);
    triggerPad(pad_idx, velocity);
}

void DrumKitSynth::triggerPad(int pad_index, float velocity) noexcept {
    if (pad_index < 0 || pad_index >= static_cast<int>(kPadCount)) return;

    DrumVoice& v = voices_[pad_index];
    v.active = true;
    v.velocity = std::clamp(velocity, 0.0f, 1.0f);
    v.env_level = 1.0f;
    v.phase1 = 0.0f;
    v.phase2 = 0.0f;
    v.pulse_count = 0;
    v.pulse_timer = 0;

    // Helper for computing exponential decay multiplier: alpha = exp(-1 / (decay_sec * sr))
    auto decayMultiplier = [this](float decay_sec) -> float {
        return std::exp(-1.0f / std::max(1.0f, decay_sec * sample_rate_));
    };

    switch (v.type) {
        case DrumPadType::KICK:
            v.current_freq = 160.0f;
            v.target_freq = 48.0f;
            v.pitch_decay_rate = 0.003f;
            v.env_decay_rate = decayMultiplier(0.38f);
            break;

        case DrumPadType::SNARE:
            v.current_freq = 220.0f;
            v.target_freq = 175.0f;
            v.pitch_decay_rate = 0.005f;
            v.env_decay_rate = decayMultiplier(0.24f);
            v.filter.setParameters(FilterType::BAND_PASS, 2200.0f, 0.0f, 1.8f);
            break;

        case DrumPadType::CLAP:
            v.pulse_count = 3; // 3 initial bursts
            v.pulse_timer = 0;
            v.env_decay_rate = decayMultiplier(0.26f);
            v.filter.setParameters(FilterType::BAND_PASS, 1400.0f, 0.0f, 1.5f);
            break;

        case DrumPadType::CLOSED_HAT:
            // Choke open hat
            voices_[static_cast<size_t>(DrumPadType::OPEN_HAT)].active = false;
            v.env_decay_rate = decayMultiplier(0.045f);
            v.filter.setParameters(FilterType::HIGH_PASS, 7500.0f, 0.0f, 2.0f);
            break;

        case DrumPadType::OPEN_HAT:
            v.env_decay_rate = decayMultiplier(0.42f);
            v.filter.setParameters(FilterType::HIGH_PASS, 7000.0f, 0.0f, 2.0f);
            break;

        case DrumPadType::SUB_808:
            v.current_freq = 80.0f;
            v.target_freq = 45.0f;
            v.pitch_decay_rate = 0.0008f;
            v.env_decay_rate = decayMultiplier(1.4f);
            break;

        case DrumPadType::LOW_TOM:
            v.current_freq = 110.0f;
            v.target_freq = 65.0f;
            v.pitch_decay_rate = 0.002f;
            v.env_decay_rate = decayMultiplier(0.45f);
            break;

        case DrumPadType::MID_TOM:
            v.current_freq = 150.0f;
            v.target_freq = 90.0f;
            v.pitch_decay_rate = 0.002f;
            v.env_decay_rate = decayMultiplier(0.40f);
            break;

        case DrumPadType::HIGH_TOM:
            v.current_freq = 200.0f;
            v.target_freq = 130.0f;
            v.pitch_decay_rate = 0.002f;
            v.env_decay_rate = decayMultiplier(0.35f);
            break;

        case DrumPadType::CRASH:
            v.env_decay_rate = decayMultiplier(1.6f);
            v.filter.setParameters(FilterType::HIGH_PASS, 4000.0f, 0.0f, 1.0f);
            break;

        case DrumPadType::RIDE:
            v.current_freq = 840.0f;
            v.env_decay_rate = decayMultiplier(0.9f);
            v.filter.setParameters(FilterType::BAND_PASS, 6000.0f, 0.0f, 2.5f);
            break;

        case DrumPadType::RIMSHOT:
            v.current_freq = 2200.0f;
            v.env_decay_rate = decayMultiplier(0.028f);
            v.filter.setParameters(FilterType::BAND_PASS, 2100.0f, 0.0f, 4.0f);
            break;

        case DrumPadType::TAMBOURINE:
            v.pulse_count = 2;
            v.pulse_timer = 0;
            v.env_decay_rate = decayMultiplier(0.18f);
            v.filter.setParameters(FilterType::HIGH_PASS, 5500.0f, 0.0f, 2.0f);
            break;

        case DrumPadType::COWBELL:
            v.current_freq = 587.0f;
            v.target_freq = 845.0f;
            v.env_decay_rate = decayMultiplier(0.25f);
            v.filter.setParameters(FilterType::BAND_PASS, 800.0f, 0.0f, 3.0f);
            break;

        case DrumPadType::CLAVES:
            v.current_freq = 2500.0f;
            v.env_decay_rate = decayMultiplier(0.035f);
            v.filter.setParameters(FilterType::BAND_PASS, 2500.0f, 0.0f, 6.0f);
            break;

        case DrumPadType::SHAKER:
            v.env_decay_rate = decayMultiplier(0.08f);
            v.filter.setParameters(FilterType::BAND_PASS, 6500.0f, 0.0f, 2.0f);
            break;
    }
}

void DrumKitSynth::noteOff(int /*note_number*/) noexcept {
    // Drum sounds decay naturally (percussive trigger)
}

void DrumKitSynth::allNotesOff() noexcept {
    for (auto& v : voices_) {
        v.active = false;
        v.env_level = 0.0f;
    }
}

void DrumKitSynth::renderBlock(float* out_left, float* out_right, size_t num_frames) noexcept {
    if (!out_left || !out_right || num_frames == 0) return;

    constexpr float kTwoPi = 6.28318530717958647692f;
    const float dt = 1.0f / sample_rate_;

    for (size_t f = 0; f < num_frames; ++f) {
        float sum_left = 0.0f;
        float sum_right = 0.0f;

        for (auto& v : voices_) {
            if (!v.active) continue;

            float sample = 0.0f;

            switch (v.type) {
                case DrumPadType::KICK: {
                    v.current_freq += (v.target_freq - v.current_freq) * v.pitch_decay_rate;
                    v.phase1 += v.current_freq * dt;
                    if (v.phase1 >= 1.0f) v.phase1 -= 1.0f;

                    // Sine sweep + punch transient
                    const float body = std::sin(v.phase1 * kTwoPi);
                    const float click = (v.env_level > 0.85f) ? (v.nextNoise() * 0.25f) : 0.0f;
                    sample = (body + click) * v.env_level;
                    break;
                }

                case DrumPadType::SNARE: {
                    v.current_freq += (v.target_freq - v.current_freq) * v.pitch_decay_rate;
                    v.phase1 += v.current_freq * dt;
                    if (v.phase1 >= 1.0f) v.phase1 -= 1.0f;

                    const float tone = std::sin(v.phase1 * kTwoPi);
                    const float noise = v.filter.processSampleLeft(v.nextNoise());
                    sample = (0.35f * tone + 0.65f * noise) * v.env_level;
                    break;
                }

                case DrumPadType::CLAP: {
                    if (v.pulse_count > 0) {
                        v.pulse_timer++;
                        if (v.pulse_timer > static_cast<int>(0.011f * sample_rate_)) {
                            v.pulse_timer = 0;
                            v.pulse_count--;
                            v.env_level = std::min(1.0f, v.env_level + 0.5f);
                        }
                    }
                    const float noise = v.filter.processSampleLeft(v.nextNoise());
                    sample = noise * v.env_level;
                    break;
                }

                case DrumPadType::CLOSED_HAT:
                case DrumPadType::OPEN_HAT: {
                    // Metallic cluster: 6 inharmonic square waves
                    static const float kMetallicFrequencies[] = {263.0f, 400.0f, 421.0f, 474.0f, 587.0f, 845.0f};
                    float metallic_sum = 0.0f;
                    v.phase1 += kMetallicFrequencies[0] * dt;
                    if (v.phase1 >= 1.0f) v.phase1 -= 1.0f;
                    metallic_sum += (v.phase1 < 0.5f ? 0.3f : -0.3f);

                    v.phase2 += kMetallicFrequencies[1] * dt;
                    if (v.phase2 >= 1.0f) v.phase2 -= 1.0f;
                    metallic_sum += (v.phase2 < 0.5f ? 0.3f : -0.3f);

                    // Add high filtered noise
                    const float raw = metallic_sum + (v.nextNoise() * 0.4f);
                    const float filtered = v.filter.processSampleLeft(raw);
                    sample = filtered * v.env_level;
                    break;
                }

                case DrumPadType::SUB_808: {
                    v.current_freq += (v.target_freq - v.current_freq) * v.pitch_decay_rate;
                    v.phase1 += v.current_freq * dt;
                    if (v.phase1 >= 1.0f) v.phase1 -= 1.0f;

                    const float sine = std::sin(v.phase1 * kTwoPi);
                    // Soft clipping saturation for 808 warmth
                    const float saturated = std::tanh(sine * 1.5f);
                    sample = saturated * v.env_level;
                    break;
                }

                case DrumPadType::LOW_TOM:
                case DrumPadType::MID_TOM:
                case DrumPadType::HIGH_TOM: {
                    v.current_freq += (v.target_freq - v.current_freq) * v.pitch_decay_rate;
                    v.phase1 += v.current_freq * dt;
                    if (v.phase1 >= 1.0f) v.phase1 -= 1.0f;

                    const float body = std::sin(v.phase1 * kTwoPi);
                    const float pop = (v.env_level > 0.9f) ? (v.nextNoise() * 0.15f) : 0.0f;
                    sample = (body + pop) * v.env_level;
                    break;
                }

                case DrumPadType::COWBELL: {
                    // Dual square oscillators
                    v.phase1 += v.current_freq * dt;
                    if (v.phase1 >= 1.0f) v.phase1 -= 1.0f;
                    v.phase2 += v.target_freq * dt;
                    if (v.phase2 >= 1.0f) v.phase2 -= 1.0f;

                    const float sq1 = (v.phase1 < 0.5f ? 0.5f : -0.5f);
                    const float sq2 = (v.phase2 < 0.5f ? 0.5f : -0.5f);
                    const float filtered = v.filter.processSampleLeft(sq1 + sq2);
                    sample = filtered * v.env_level;
                    break;
                }

                case DrumPadType::CRASH:
                case DrumPadType::RIDE: {
                    const float noise = v.nextNoise();
                    const float filtered = v.filter.processSampleLeft(noise);
                    sample = filtered * v.env_level;
                    break;
                }

                case DrumPadType::RIMSHOT:
                case DrumPadType::CLAVES: {
                    v.phase1 += v.current_freq * dt;
                    if (v.phase1 >= 1.0f) v.phase1 -= 1.0f;
                    const float ping = std::sin(v.phase1 * kTwoPi);
                    const float filtered = v.filter.processSampleLeft(ping);
                    sample = filtered * v.env_level;
                    break;
                }

                case DrumPadType::TAMBOURINE:
                case DrumPadType::SHAKER:
                default: {
                    const float noise = v.nextNoise();
                    const float filtered = v.filter.processSampleLeft(noise);
                    sample = filtered * v.env_level;
                    break;
                }
            }

            v.env_level *= v.env_decay_rate;
            if (v.env_level <= 0.0005f) {
                v.active = false;
                v.env_level = 0.0f;
            }

            const float weighted = sample * v.velocity;
            sum_left += weighted;
            sum_right += weighted;
        }

        out_left[f] += sum_left * master_gain_;
        out_right[f] += sum_right * master_gain_;
    }
}

} // namespace dsp
} // namespace daw

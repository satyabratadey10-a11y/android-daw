#ifndef ANDROID_DAW_SUB_SYNTH_H
#define ANDROID_DAW_SUB_SYNTH_H

#include <cstdint>
#include <cstddef>
#include <array>
#include <vector>
#include <cmath>
#include <algorithm>
#include "BiquadFilter.h"
#include "ParameterSmoother.h"

namespace daw {
namespace dsp {

enum class OscWaveform : int {
    SAW = 0,
    SQUARE = 1,
    SINE = 2,
    TRIANGLE = 3
};

enum class EnvelopeStage : int {
    IDLE = 0,
    ATTACK = 1,
    DECAY = 2,
    SUSTAIN = 3,
    RELEASE = 4
};

struct AdsrParameters {
    float attack_sec{0.01f};
    float decay_sec{0.25f};
    float sustain_level{0.7f};
    float release_sec{0.3f};
};

class AdsrEnvelope {
public:
    void setSampleRate(float sr) noexcept {
        sample_rate_ = sr > 0.0f ? sr : 48000.0f;
    }

    void trigger(float velocity = 1.0f) noexcept {
        velocity_ = std::clamp(velocity, 0.0f, 1.0f);
        stage_ = EnvelopeStage::ATTACK;
        // Don't reset current_value to 0 to avoid clicks when retriggering
    }

    void release() noexcept {
        if (stage_ != EnvelopeStage::IDLE) {
            stage_ = EnvelopeStage::RELEASE;
        }
    }

    void stop() noexcept {
        stage_ = EnvelopeStage::IDLE;
        current_value_ = 0.0f;
    }

    [[nodiscard]] bool isActive() const noexcept {
        return stage_ != EnvelopeStage::IDLE;
    }

    inline float nextSample(const AdsrParameters& params) noexcept {
        switch (stage_) {
            case EnvelopeStage::ATTACK: {
                const float attack_samples = std::max(1.0f, params.attack_sec * sample_rate_);
                current_value_ += (1.0f / attack_samples);
                if (current_value_ >= 1.0f) {
                    current_value_ = 1.0f;
                    stage_ = EnvelopeStage::DECAY;
                }
                break;
            }
            case EnvelopeStage::DECAY: {
                const float decay_samples = std::max(1.0f, params.decay_sec * sample_rate_);
                const float decay_step = (1.0f - params.sustain_level) / decay_samples;
                current_value_ -= decay_step;
                if (current_value_ <= params.sustain_level) {
                    current_value_ = params.sustain_level;
                    stage_ = EnvelopeStage::SUSTAIN;
                }
                break;
            }
            case EnvelopeStage::SUSTAIN: {
                current_value_ = params.sustain_level;
                break;
            }
            case EnvelopeStage::RELEASE: {
                const float release_samples = std::max(1.0f, params.release_sec * sample_rate_);
                const float release_step = params.sustain_level / release_samples;
                current_value_ -= release_step;
                if (current_value_ <= 0.0001f) {
                    current_value_ = 0.0f;
                    stage_ = EnvelopeStage::IDLE;
                }
                break;
            }
            case EnvelopeStage::IDLE:
            default:
                current_value_ = 0.0f;
                break;
        }
        return current_value_ * velocity_;
    }

    [[nodiscard]] float getCurrentValue() const noexcept {
        return current_value_ * velocity_;
    }

private:
    float sample_rate_{48000.0f};
    float current_value_{0.0f};
    float velocity_{1.0f};
    EnvelopeStage stage_{EnvelopeStage::IDLE};
};

/**
 * 16-Voice Polyphonic Subtractive Synthesizer (FL Studio Mobile Sub Synth fidelity).
 * Features:
 * - 2 polyBLEP anti-aliased oscillators (Saw, Square, Sine, Triangle)
 * - Detune & Semitone pitch offset for Osc 2
 * - ADSR Volume Envelope & Filter Envelope
 * - 2-pole resonant low-pass filter
 * - Dynamic voice allocation with LRU voice stealing
 * - Zero allocations on the audio callback thread
 */
class SubSynth {
public:
    static constexpr size_t kMaxVoices = 16;

    explicit SubSynth(float sample_rate = 48000.0f);
    ~SubSynth() = default;

    void setSampleRate(float sample_rate) noexcept;
    void reset() noexcept;

    // Realtime MIDI events
    void noteOn(int note_number, float velocity) noexcept;
    void noteOff(int note_number) noexcept;
    void allNotesOff() noexcept;

    // Audio block rendering (additive stereo output)
    void renderBlock(float* out_left, float* out_right, size_t num_frames) noexcept;

    // Parameters
    void setOsc1Waveform(OscWaveform wave) noexcept { osc1_wave_ = wave; }
    void setOsc2Waveform(OscWaveform wave) noexcept { osc2_wave_ = wave; }
    void setOsc2Semitone(int semi) noexcept { osc2_semitone_ = semi; }
    void setOsc2DetuneCents(float cents) noexcept { osc2_detune_cents_ = cents; }
    void setOscMix(float mix) noexcept { osc_mix_ = std::clamp(mix, 0.0f, 1.0f); }

    void setFilterCutoff(float cutoff_hz) noexcept { filter_cutoff_hz_ = std::clamp(cutoff_hz, 20.0f, 20000.0f); }
    void setFilterResonance(float q) noexcept { filter_q_ = std::clamp(q, 0.5f, 10.0f); }
    void setFilterEnvAmount(float amount) noexcept { filter_env_amount_ = std::clamp(amount, -1.0f, 1.0f); }

    void setAmpAdsr(float a, float d, float s, float r) noexcept {
        amp_params_.attack_sec = std::max(0.001f, a);
        amp_params_.decay_sec = std::max(0.001f, d);
        amp_params_.sustain_level = std::clamp(s, 0.0f, 1.0f);
        amp_params_.release_sec = std::max(0.001f, r);
    }

    void setFilterAdsr(float a, float d, float s, float r) noexcept {
        filter_params_.attack_sec = std::max(0.001f, a);
        filter_params_.decay_sec = std::max(0.001f, d);
        filter_params_.sustain_level = std::clamp(s, 0.0f, 1.0f);
        filter_params_.release_sec = std::max(0.001f, r);
    }

    void setMasterGain(float gain) noexcept { master_gain_ = std::max(0.0f, gain); }

private:
    struct Voice {
        bool active{false};
        int note{-1};
        float velocity{0.0f};
        uint64_t age{0};

        float phase1{0.0f};
        float phase2{0.0f};
        float phase_inc1{0.0f};
        float phase_inc2{0.0f};

        AdsrEnvelope amp_env;
        AdsrEnvelope filter_env;
        BiquadFilter filter;
    };

    static inline float polyBLEP(float t, float dt) noexcept {
        if (t < dt) {
            t /= dt;
            return t + t - t * t - 1.0f;
        } else if (t > 1.0f - dt) {
            t = (t - 1.0f) / dt;
            return t * t + t + t + 1.0f;
        }
        return 0.0f;
    }

    static inline float generateOsc(OscWaveform wave, float phase, float dt) noexcept {
        switch (wave) {
            case OscWaveform::SAW: {
                float val = 2.0f * phase - 1.0f;
                val -= polyBLEP(phase, dt);
                return val;
            }
            case OscWaveform::SQUARE: {
                float val = (phase < 0.5f) ? 1.0f : -1.0f;
                val += polyBLEP(phase, dt);
                val -= polyBLEP(std::fmod(phase + 0.5f, 1.0f), dt);
                return val;
            }
            case OscWaveform::TRIANGLE: {
                // Derived from integrated square or formula
                float saw = 2.0f * phase - 1.0f;
                saw -= polyBLEP(phase, dt);
                return 2.0f * std::abs(saw) - 1.0f;
            }
            case OscWaveform::SINE:
            default:
                return std::sin(phase * 6.28318530717958647692f);
        }
    }

    float noteToFrequency(int note, int semi_offset = 0, float cents_offset = 0.0f) const noexcept;

    float sample_rate_{48000.0f};
    uint64_t global_age_{0};

    OscWaveform osc1_wave_{OscWaveform::SAW};
    OscWaveform osc2_wave_{OscWaveform::SQUARE};
    int osc2_semitone_{0};
    float osc2_detune_cents_{7.0f}; // Default gentle 7 cents chorus detune
    float osc_mix_{0.5f};

    float filter_cutoff_hz_{3500.0f};
    float filter_q_{1.5f};
    float filter_env_amount_{0.4f};

    AdsrParameters amp_params_;
    AdsrParameters filter_params_;

    float master_gain_{0.5f};

    std::array<Voice, kMaxVoices> voices_;
};

} // namespace dsp
} // namespace daw

#endif // ANDROID_DAW_SUB_SYNTH_H

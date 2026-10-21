#include "SubSynth.h"
#include <cmath>
#include <algorithm>

namespace daw {
namespace dsp {

SubSynth::SubSynth(float sample_rate)
    : sample_rate_(sample_rate > 0.0f ? sample_rate : 48000.0f) {
    for (auto& voice : voices_) {
        voice.filter.setSampleRate(sample_rate_);
        voice.filter.setParameters(FilterType::LOW_PASS, filter_cutoff_hz_, 0.0f, filter_q_);
        voice.amp_env.setSampleRate(sample_rate_);
        voice.filter_env.setSampleRate(sample_rate_);
    }
}

void SubSynth::setSampleRate(float sample_rate) noexcept {
    if (sample_rate <= 0.0f) return;
    sample_rate_ = sample_rate;
    for (auto& voice : voices_) {
        voice.filter.setSampleRate(sample_rate_);
        voice.amp_env.setSampleRate(sample_rate_);
        voice.filter_env.setSampleRate(sample_rate_);
    }
}

void SubSynth::reset() noexcept {
    for (auto& voice : voices_) {
        voice.active = false;
        voice.note = -1;
        voice.amp_env.stop();
        voice.filter_env.stop();
        voice.filter.reset();
    }
}

float SubSynth::noteToFrequency(int note, int semi_offset, float cents_offset) const noexcept {
    const float total_semi = static_cast<float>(note + semi_offset - 69) + (cents_offset / 100.0f);
    return 440.0f * std::pow(2.0f, total_semi / 12.0f);
}

void SubSynth::noteOn(int note_number, float velocity) noexcept {
    if (velocity <= 0.0f) {
        noteOff(note_number);
        return;
    }

    global_age_++;

    // 1. Try to find if this note is already playing (retrigger)
    Voice* target_voice = nullptr;
    for (auto& voice : voices_) {
        if (voice.active && voice.note == note_number) {
            target_voice = &voice;
            break;
        }
    }

    // 2. Look for an inactive voice
    if (!target_voice) {
        for (auto& voice : voices_) {
            if (!voice.active) {
                target_voice = &voice;
                break;
            }
        }
    }

    // 3. Voice steal: pick oldest voice
    if (!target_voice) {
        uint64_t oldest_age = UINT64_MAX;
        for (auto& voice : voices_) {
            if (voice.age < oldest_age) {
                oldest_age = voice.age;
                target_voice = &voice;
            }
        }
    }

    if (!target_voice) return;

    target_voice->active = true;
    target_voice->note = note_number;
    target_voice->velocity = velocity;
    target_voice->age = global_age_;

    const float f1 = noteToFrequency(note_number, 0, 0.0f);
    const float f2 = noteToFrequency(note_number, osc2_semitone_, osc2_detune_cents_);

    target_voice->phase_inc1 = std::clamp(f1 / sample_rate_, 0.0001f, 0.49f);
    target_voice->phase_inc2 = std::clamp(f2 / sample_rate_, 0.0001f, 0.49f);

    target_voice->amp_env.trigger(velocity);
    target_voice->filter_env.trigger(velocity);
}

void SubSynth::noteOff(int note_number) noexcept {
    for (auto& voice : voices_) {
        if (voice.active && voice.note == note_number) {
            voice.amp_env.release();
            voice.filter_env.release();
        }
    }
}

void SubSynth::allNotesOff() noexcept {
    for (auto& voice : voices_) {
        if (voice.active) {
            voice.amp_env.release();
            voice.filter_env.release();
        }
    }
}

void SubSynth::renderBlock(float* out_left, float* out_right, size_t num_frames) noexcept {
    if (!out_left || !out_right || num_frames == 0) return;

    for (size_t f = 0; f < num_frames; ++f) {
        float sum_sample = 0.0f;

        for (auto& voice : voices_) {
            if (!voice.active) continue;

            const float amp = voice.amp_env.nextSample(amp_params_);
            if (!voice.amp_env.isActive()) {
                voice.active = false;
                continue;
            }

            // Synthesize Osc 1 & Osc 2
            const float s1 = generateOsc(osc1_wave_, voice.phase1, voice.phase_inc1);
            const float s2 = generateOsc(osc2_wave_, voice.phase2, voice.phase_inc2);

            voice.phase1 += voice.phase_inc1;
            if (voice.phase1 >= 1.0f) voice.phase1 -= 1.0f;

            voice.phase2 += voice.phase_inc2;
            if (voice.phase2 >= 1.0f) voice.phase2 -= 1.0f;

            const float raw_mix = (1.0f - osc_mix_) * s1 + osc_mix_ * s2;

            // Modulate filter cutoff with filter envelope
            const float f_env = voice.filter_env.nextSample(filter_params_);
            const float modulated_cutoff = filter_cutoff_hz_ * std::pow(2.0f, f_env * filter_env_amount_ * 4.0f);
            const float clamped_cutoff = std::clamp(modulated_cutoff, 20.0f, sample_rate_ * 0.45f);

            voice.filter.setParameters(FilterType::LOW_PASS, clamped_cutoff, 0.0f, filter_q_);
            const float filtered = voice.filter.processSampleLeft(raw_mix);

            sum_sample += (filtered * amp);
        }

        const float out_s = sum_sample * master_gain_;
        out_left[f] += out_s;
        out_right[f] += out_s;
    }
}

} // namespace dsp
} // namespace daw

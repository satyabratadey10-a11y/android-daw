#ifndef ANDROID_DAW_DRUM_KIT_SYNTH_H
#define ANDROID_DAW_DRUM_KIT_SYNTH_H

#include <cstdint>
#include <cstddef>
#include <array>
#include <cmath>
#include <algorithm>
#include "BiquadFilter.h"

namespace daw {
namespace dsp {

enum class DrumPadType : int {
    KICK = 0,         // Note 36 (C1)
    SNARE = 1,        // Note 38 (D1)
    CLAP = 2,         // Note 39 (D#1)
    CLOSED_HAT = 3,   // Note 42 (F#1)
    OPEN_HAT = 4,     // Note 46 (A#1)
    SUB_808 = 5,      // Note 35 (B0)
    LOW_TOM = 6,      // Note 41 (F1)
    MID_TOM = 7,      // Note 45 (A1)
    HIGH_TOM = 8,     // Note 48 (C2)
    CRASH = 9,        // Note 49 (C#2)
    RIDE = 10,        // Note 51 (D#2)
    RIMSHOT = 11,     // Note 37 (C#1)
    TAMBOURINE = 12,  // Note 54 (F#2)
    COWBELL = 13,     // Note 56 (G#2)
    CLAVES = 14,      // Note 75 (D#4)
    SHAKER = 15       // Note 70 (A#3)
};

/**
 * Procedural Drum Kit Synthesizer (16 Pads).
 * Synthesizes classic acoustic and electronic drum sounds via pure DSP (zero licensed samples needed).
 * Real-time safe, non-blocking, and polyphonic.
 */
class DrumKitSynth {
public:
    static constexpr size_t kPadCount = 16;

    explicit DrumKitSynth(float sample_rate = 48000.0f);
    ~DrumKitSynth() = default;

    void setSampleRate(float sample_rate) noexcept;
    void reset() noexcept;

    // Trigger pad via MIDI note or pad index (0-15)
    void noteOn(int note_number, float velocity) noexcept;
    void triggerPad(int pad_index, float velocity) noexcept;
    void noteOff(int note_number) noexcept;
    void allNotesOff() noexcept;

    // Additive stereo block render
    void renderBlock(float* out_left, float* out_right, size_t num_frames) noexcept;

    void setMasterGain(float gain) noexcept { master_gain_ = std::max(0.0f, gain); }

private:
    struct DrumVoice {
        bool active{false};
        int note{0};
        DrumPadType type{DrumPadType::KICK};
        float velocity{1.0f};

        float phase1{0.0f};
        float phase2{0.0f};
        float current_freq{0.0f};
        float target_freq{0.0f};
        float pitch_decay_rate{0.0f};

        float env_level{0.0f};
        float env_decay_rate{0.0f};

        // Multi-pulse state for Clap & Tambourine
        int pulse_count{0};
        int pulse_timer{0};

        BiquadFilter filter;
        uint32_t noise_seed{123456789};

        inline float nextNoise() noexcept {
            noise_seed = noise_seed * 1664525u + 1013904223u;
            return static_cast<float>(static_cast<int32_t>(noise_seed)) / 2147483648.0f;
        }
    };

    int noteToPadIndex(int note_number) const noexcept;

    float sample_rate_{48000.0f};
    float master_gain_{0.8f};

    std::array<DrumVoice, kPadCount> voices_;
};

} // namespace dsp
} // namespace daw

#endif // ANDROID_DAW_DRUM_KIT_SYNTH_H

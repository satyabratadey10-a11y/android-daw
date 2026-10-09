#ifndef ANDROID_DAW_MIXDOWN_RENDERER_H
#define ANDROID_DAW_MIXDOWN_RENDERER_H

#include <string>
#include <functional>
#include <cstdint>
#include <cstddef>

namespace daw {
namespace audio {

class AudioEngine;

/**
 * MixdownRenderer: Faster-than-realtime offline mixdown renderer.
 * Bounces all project tracks, DSP chains, and master limiter into 16-bit or 24-bit stereo WAV.
 */
class MixdownRenderer {
public:
    using ProgressCallback = std::function<void(float progress)>;

    static bool renderOffline(AudioEngine& engine,
                              const std::string& output_wav_path,
                              int bit_depth,
                              int64_t total_frames,
                              ProgressCallback progress_callback = nullptr);
};

} // namespace audio
} // namespace daw

#endif // ANDROID_DAW_MIXDOWN_RENDERER_H

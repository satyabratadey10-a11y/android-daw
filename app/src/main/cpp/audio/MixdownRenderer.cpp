#include "MixdownRenderer.h"
#include "AudioEngine.h"
#include "WavFile.h"
#include <vector>
#include <algorithm>

namespace daw {
namespace audio {

bool MixdownRenderer::renderOffline(AudioEngine& engine,
                                   const std::string& output_wav_path,
                                   int bit_depth,
                                   int64_t total_frames,
                                   ProgressCallback progress_callback) {
    if (output_wav_path.empty() || total_frames <= 0) {
        return false;
    }

    const uint32_t sample_rate = static_cast<uint32_t>(engine.getSampleRate());
    const int bits = (bit_depth == 16) ? 16 : 24;

    WavWriter writer(output_wav_path, sample_rate, 2, bits);
    if (!writer.isOpen()) {
        return false;
    }

    constexpr size_t kBlockSize = 2048;
    std::vector<float> block_left(kBlockSize, 0.0f);
    std::vector<float> block_right(kBlockSize, 0.0f);

    int64_t current_frame = 0;
    while (current_frame < total_frames) {
        const size_t frames_to_render = static_cast<size_t>(
            std::min(static_cast<int64_t>(kBlockSize), total_frames - current_frame)
        );

        // Render planar audio through entire multi-track engine and master bus
        engine.renderAudioPlanar(block_left.data(), block_right.data(), frames_to_render, current_frame);

        // Write planar chunk to disk with TPDF dither
        if (!writer.writePlanar(block_left.data(), block_right.data(), frames_to_render)) {
            writer.close();
            return false;
        }

        current_frame += frames_to_render;

        if (progress_callback) {
            progress_callback(static_cast<float>(current_frame) / static_cast<float>(total_frames));
        }
    }

    return writer.close();
}

} // namespace audio
} // namespace daw

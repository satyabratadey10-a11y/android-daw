#ifndef ANDROID_DAW_AAUDIO_STREAM_MANAGER_H
#define ANDROID_DAW_AAUDIO_STREAM_MANAGER_H

#include <aaudio/AAudio.h>
#include <atomic>
#include <mutex>
#include <thread>
#include <cstdint>

namespace daw {
namespace audio {
class AudioEngine;
}

namespace backend {

/**
 * AAudioStreamManager: Handles lifecycle, callbacks, and buffer tuning for AAudio streams.
 * Configures low-latency exclusive output streams with double burst buffering.
 */
class AAudioStreamManager {
public:
    explicit AAudioStreamManager(audio::AudioEngine& engine);
    ~AAudioStreamManager();

    // Lifecycle
    bool start();
    bool pause();
    bool stop();
    bool restart();
    void close();

    [[nodiscard]] bool isRunning() const noexcept { return is_running_.load(std::memory_order_relaxed); }
    [[nodiscard]] int getSampleRate() const noexcept { return sample_rate_; }
    [[nodiscard]] int getFramesPerBurst() const noexcept { return frames_per_burst_; }
    [[nodiscard]] int getBufferSizeInFrames() const noexcept;
    [[nodiscard]] int32_t getXRunCount() const noexcept;

    // AAudio Callback Handlers
    static aaudio_data_callback_result_t dataCallback(AAudioStream* stream,
                                                      void* userData,
                                                      void* audioData,
                                                      int32_t numFrames);

    static void errorCallback(AAudioStream* stream,
                              void* userData,
                              aaudio_result_t error);

    aaudio_data_callback_result_t onAudioData(float* audioData, int32_t numFrames);
    void onError(aaudio_result_t error);

private:
    bool openStream();

    audio::AudioEngine& engine_;
    AAudioStream* stream_{nullptr};
    std::mutex stream_mutex_;

    std::atomic<bool> is_running_{false};
    std::atomic<bool> is_restarting_{false};

    int sample_rate_{48000};
    int frames_per_burst_{192};
};

} // namespace backend
} // namespace daw

#endif // ANDROID_DAW_AAUDIO_STREAM_MANAGER_H

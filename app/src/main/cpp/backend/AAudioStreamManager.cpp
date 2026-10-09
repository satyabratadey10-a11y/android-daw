#include "AAudioStreamManager.h"
#include "../audio/AudioEngine.h"
#include <android/log.h>
#include <algorithm>

#define LOG_TAG "DAW_AAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace daw {
namespace backend {

AAudioStreamManager::AAudioStreamManager(audio::AudioEngine& engine)
    : engine_(engine) {
}

AAudioStreamManager::~AAudioStreamManager() {
    close();
}

bool AAudioStreamManager::openStream() {
    std::lock_guard<std::mutex> lock(stream_mutex_);

    if (stream_ != nullptr) {
        return true;
    }

    AAudioStreamBuilder* builder = nullptr;
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    if (result != AAUDIO_OK || !builder) {
        LOGE("Failed to create AAudioStreamBuilder: %s", AAudio_convertResultToText(result));
        return false;
    }

    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setChannelCount(builder, 2);
    AAudioStreamBuilder_setSampleRate(builder, sample_rate_);
    AAudioStreamBuilder_setDataCallback(builder, dataCallback, this);
    AAudioStreamBuilder_setErrorCallback(builder, errorCallback, this);

    // Attempt to open exclusive low-latency stream
    result = AAudioStreamBuilder_openStream(builder, &stream_);
    if (result != AAUDIO_OK) {
        LOGW("Exclusive stream open failed (%s), falling back to SHARED mode", AAudio_convertResultToText(result));
        AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
        result = AAudioStreamBuilder_openStream(builder, &stream_);
    }

    AAudioStreamBuilder_delete(builder);

    if (result != AAUDIO_OK || !stream_) {
        LOGE("Failed to open AAudio output stream: %s", AAudio_convertResultToText(result));
        stream_ = nullptr;
        return false;
    }

    // Read hardware characteristics
    sample_rate_ = AAudioStream_getSampleRate(stream_);
    frames_per_burst_ = AAudioStream_getFramesPerBurst(stream_);

    // Set double burst buffering for low latency without under-runs
    const int32_t target_buffer_size = frames_per_burst_ * 2;
    AAudioStream_setBufferSizeInFrames(stream_, target_buffer_size);

    LOGI("AAudio stream opened: sampleRate=%d, burst=%d, bufferSize=%d",
         sample_rate_, frames_per_burst_, AAudioStream_getBufferSizeInFrames(stream_));

    engine_.init(sample_rate_, frames_per_burst_);
    return true;
}

bool AAudioStreamManager::start() {
    if (!stream_) {
        if (!openStream()) {
            return false;
        }
    }

    std::lock_guard<std::mutex> lock(stream_mutex_);
    if (!stream_) return false;

    aaudio_stream_state_t state = AAudioStream_getState(stream_);
    if (state == AAUDIO_STREAM_STATE_STARTED) {
        is_running_.store(true, std::memory_order_release);
        return true;
    }

    aaudio_result_t result = AAudioStream_requestStart(stream_);
    if (result != AAUDIO_OK) {
        LOGE("Failed to start AAudio stream: %s", AAudio_convertResultToText(result));
        return false;
    }

    is_running_.store(true, std::memory_order_release);
    return true;
}

bool AAudioStreamManager::pause() {
    std::lock_guard<std::mutex> lock(stream_mutex_);
    if (!stream_) return false;

    is_running_.store(false, std::memory_order_release);
    aaudio_result_t result = AAudioStream_requestPause(stream_);
    if (result != AAUDIO_OK) {
        LOGW("AAudioStream_requestPause error: %s", AAudio_convertResultToText(result));
        return false;
    }
    return true;
}

bool AAudioStreamManager::stop() {
    std::lock_guard<std::mutex> lock(stream_mutex_);
    if (!stream_) return false;

    is_running_.store(false, std::memory_order_release);
    aaudio_result_t result = AAudioStream_requestStop(stream_);
    if (result != AAUDIO_OK) {
        LOGW("AAudioStream_requestStop error: %s", AAudio_convertResultToText(result));
        return false;
    }
    return true;
}

void AAudioStreamManager::close() {
    stop();
    std::lock_guard<std::mutex> lock(stream_mutex_);
    if (stream_) {
        AAudioStream_close(stream_);
        stream_ = nullptr;
    }
}

bool AAudioStreamManager::restart() {
    if (is_restarting_.exchange(true)) {
        return false; // Prevent concurrent restart re-entry
    }

    LOGI("Restarting AAudio stream after device disconnection / error...");
    close();
    bool success = openStream() && start();
    is_restarting_.store(false);
    return success;
}

int AAudioStreamManager::getBufferSizeInFrames() const noexcept {
    if (stream_) {
        return AAudioStream_getBufferSizeInFrames(stream_);
    }
    return frames_per_burst_ * 2;
}

int32_t AAudioStreamManager::getXRunCount() const noexcept {
    if (stream_) {
        return AAudioStream_getXRunCount(stream_);
    }
    return 0;
}

aaudio_data_callback_result_t AAudioStreamManager::dataCallback(AAudioStream* /*stream*/,
                                                               void* userData,
                                                               void* audioData,
                                                               int32_t numFrames) {
    auto* manager = static_cast<AAudioStreamManager*>(userData);
    if (!manager) {
        return AAUDIO_CALLBACK_RESULT_STOP;
    }
    return manager->onAudioData(static_cast<float*>(audioData), numFrames);
}

void AAudioStreamManager::errorCallback(AAudioStream* /*stream*/,
                                       void* userData,
                                       aaudio_result_t error) {
    auto* manager = static_cast<AAudioStreamManager*>(userData);
    if (manager) {
        manager->onError(error);
    }
}

aaudio_data_callback_result_t AAudioStreamManager::onAudioData(float* audioData, int32_t numFrames) {
    if (!audioData || numFrames <= 0) {
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }

    // Real-time render callback into AudioEngine
    engine_.renderAudio(audioData, static_cast<size_t>(numFrames));
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void AAudioStreamManager::onError(aaudio_result_t error) {
    LOGW("AAudio asynchronous error received: %s", AAudio_convertResultToText(error));

    if (error == AAUDIO_ERROR_DISCONNECTED) {
        LOGI("Audio device disconnected (e.g. headphones unplugged), scheduling stream restart");
        std::thread([this]() {
            restart();
        }).detach();
    }
}

} // namespace backend
} // namespace daw

#include "JniBridge.h"
#include "../audio/AudioEngine.h"
#include "../backend/AAudioStreamManager.h"
#include <android/log.h>
#include <memory>
#include <mutex>
#include <string>

#define LOG_TAG "DAW_JniBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::mutex g_engine_mutex;
static std::unique_ptr<daw::audio::AudioEngine> g_audio_engine;
static std::unique_ptr<daw::backend::AAudioStreamManager> g_stream_manager;

// =============================================================================
// Helper String Converter
// =============================================================================

static std::string jstringToString(JNIEnv* env, jstring jstr) {
    if (!jstr) return "";
    const char* utf = env->GetStringUTFChars(jstr, nullptr);
    if (!utf) return "";
    std::string str(utf);
    env->ReleaseStringUTFChars(jstr, utf);
    return str;
}

// =============================================================================
// Native Implementation Functions
// =============================================================================

static void jni_nativeInit(JNIEnv* /*env*/, jobject /*thiz*/, jint sampleRate, jint framesPerBurst) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    LOGI("nativeInit: sampleRate=%d, framesPerBurst=%d", sampleRate, framesPerBurst);

    if (!g_audio_engine) {
        g_audio_engine = std::make_unique<daw::audio::AudioEngine>();
    }
    if (!g_stream_manager) {
        g_stream_manager = std::make_unique<daw::backend::AAudioStreamManager>(*g_audio_engine);
    }

    g_audio_engine->init(sampleRate, framesPerBurst);
    g_stream_manager->start();
}

static void jni_nativeRelease(JNIEnv* /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    LOGI("nativeRelease");

    if (g_stream_manager) {
        g_stream_manager->close();
        g_stream_manager.reset();
    }
    if (g_audio_engine) {
        g_audio_engine->release();
        g_audio_engine.reset();
    }
}

static void jni_nativePlay(JNIEnv* /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (g_stream_manager && !g_stream_manager->isRunning()) {
        g_stream_manager->start();
    }
    if (g_audio_engine) {
        g_audio_engine->play();
    }
}

static void jni_nativePause(JNIEnv* /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (g_audio_engine) {
        g_audio_engine->pause();
    }
}

static void jni_nativeStop(JNIEnv* /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (g_audio_engine) {
        g_audio_engine->stop();
    }
}

static void jni_nativeSeek(JNIEnv* /*env*/, jobject /*thiz*/, jlong framePosition) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (g_audio_engine) {
        g_audio_engine->seek(static_cast<int64_t>(framePosition));
    }
}

static void jni_nativeSetLoop(JNIEnv* /*env*/, jobject /*thiz*/, jboolean enabled, jlong startFrame, jlong endFrame) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (g_audio_engine) {
        g_audio_engine->setLoop(enabled == JNI_TRUE, static_cast<int64_t>(startFrame), static_cast<int64_t>(endFrame));
    }
}

static void jni_nativeSetTempo(JNIEnv* /*env*/, jobject /*thiz*/, jdouble bpm) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (g_audio_engine) {
        g_audio_engine->setTempo(static_cast<double>(bpm));
    }
}

static jint jni_nativeAddTrack(JNIEnv* env, jobject /*thiz*/, jstring name) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (!g_audio_engine) return -1;
    const std::string track_name = jstringToString(env, name);
    return g_audio_engine->addTrack(track_name.empty() ? "Track" : track_name);
}

static void jni_nativeRemoveTrack(JNIEnv* /*env*/, jobject /*thiz*/, jint trackId) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (g_audio_engine) {
        g_audio_engine->removeTrack(trackId);
    }
}

static void jni_nativeSetTrackVolume(JNIEnv* /*env*/, jobject /*thiz*/, jint trackId, jfloat linearGain) {
    if (g_audio_engine) {
        g_audio_engine->setTrackVolume(trackId, linearGain);
    }
}

static void jni_nativeSetTrackPan(JNIEnv* /*env*/, jobject /*thiz*/, jint trackId, jfloat panPosition) {
    if (g_audio_engine) {
        g_audio_engine->setTrackPan(trackId, panPosition);
    }
}

static void jni_nativeSetTrackMute(JNIEnv* /*env*/, jobject /*thiz*/, jint trackId, jboolean muted) {
    if (g_audio_engine) {
        g_audio_engine->setTrackMute(trackId, muted == JNI_TRUE);
    }
}

static void jni_nativeSetTrackSolo(JNIEnv* /*env*/, jobject /*thiz*/, jint trackId, jboolean soloed) {
    if (g_audio_engine) {
        g_audio_engine->setTrackSolo(trackId, soloed == JNI_TRUE);
    }
}

static void jni_nativeSetTrackArmed(JNIEnv* /*env*/, jobject /*thiz*/, jint trackId, jboolean armed) {
    if (g_audio_engine) {
        g_audio_engine->setTrackArmed(trackId, armed == JNI_TRUE);
    }
}

static void jni_nativeSetTrackEqBand(JNIEnv* /*env*/, jobject /*thiz*/, jint trackId, jint bandIndex, jint filterType, jfloat freqHz, jfloat gainDb, jfloat qFactor) {
    if (g_audio_engine) {
        g_audio_engine->setTrackEqBand(trackId, bandIndex, filterType, freqHz, gainDb, qFactor);
    }
}

static void jni_nativeSetTrackDelay(JNIEnv* /*env*/, jobject /*thiz*/, jint trackId, jfloat delayMs, jfloat feedback, jfloat wetDry) {
    if (g_audio_engine) {
        g_audio_engine->setTrackDelay(trackId, delayMs, feedback, wetDry);
    }
}

static void jni_nativeSetMasterLimiter(JNIEnv* /*env*/, jobject /*thiz*/, jfloat thresholdDb, jfloat ceilingDb, jfloat releaseMs) {
    if (g_audio_engine) {
        g_audio_engine->setMasterLimiter(thresholdDb, ceilingDb, releaseMs);
    }
}

static jlong jni_nativeGetPlaybackPositionFrames(JNIEnv* /*env*/, jobject /*thiz*/) {
    if (g_audio_engine) {
        return static_cast<jlong>(g_audio_engine->getPlaybackPositionFrames());
    }
    return 0;
}

static void jni_nativeGetMeteringData(JNIEnv* env, jobject /*thiz*/, jobject directFloatBuffer, jint numTracks) {
    if (!directFloatBuffer || !g_audio_engine) return;

    void* bufferAddress = env->GetDirectBufferAddress(directFloatBuffer);
    if (!bufferAddress) return;

    float* floatBuf = static_cast<float*>(bufferAddress);
    g_audio_engine->getMeteringData(floatBuf, numTracks);
}

static jboolean jni_nativeLoadClip(JNIEnv* env, jobject /*thiz*/, jint trackId, jint clipId, jstring wavFilePath, jlong startOffsetFrames) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (!g_audio_engine) return JNI_FALSE;

    const std::string path = jstringToString(env, wavFilePath);
    if (path.empty()) return JNI_FALSE;

    bool success = g_audio_engine->loadClip(trackId, clipId, path, static_cast<int64_t>(startOffsetFrames));
    return success ? JNI_TRUE : JNI_FALSE;
}

static jboolean jni_nativeStartRecording(JNIEnv* env, jobject /*thiz*/, jint trackId, jstring destinationWavPath) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (!g_audio_engine) return JNI_FALSE;

    const std::string path = jstringToString(env, destinationWavPath);
    if (path.empty()) return JNI_FALSE;

    bool success = g_audio_engine->startRecording(trackId, path);
    return success ? JNI_TRUE : JNI_FALSE;
}

static void jni_nativeStopRecording(JNIEnv* /*env*/, jobject /*thiz*/, jint trackId) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (g_audio_engine) {
        g_audio_engine->stopRecording(trackId);
    }
}

static jboolean jni_nativeRenderMixdown(JNIEnv* env, jobject /*thiz*/, jstring outputWavPath, jint bitDepth, jlong totalFrames) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (!g_audio_engine) return JNI_FALSE;

    const std::string path = jstringToString(env, outputWavPath);
    if (path.empty()) return JNI_FALSE;

    bool success = g_audio_engine->renderMixdown(path, bitDepth, static_cast<int64_t>(totalFrames));
    return success ? JNI_TRUE : JNI_FALSE;
}

// =============================================================================
// Native Method Registration Table
// =============================================================================

static const JNINativeMethod g_native_methods[] = {
    {"nativeInit", "(II)V", reinterpret_cast<void*>(jni_nativeInit)},
    {"nativeRelease", "()V", reinterpret_cast<void*>(jni_nativeRelease)},
    {"nativePlay", "()V", reinterpret_cast<void*>(jni_nativePlay)},
    {"nativePause", "()V", reinterpret_cast<void*>(jni_nativePause)},
    {"nativeStop", "()V", reinterpret_cast<void*>(jni_nativeStop)},
    {"nativeSeek", "(J)V", reinterpret_cast<void*>(jni_nativeSeek)},
    {"nativeSetLoop", "(ZJJ)V", reinterpret_cast<void*>(jni_nativeSetLoop)},
    {"nativeSetTempo", "(D)V", reinterpret_cast<void*>(jni_nativeSetTempo)},
    {"nativeAddTrack", "(Ljava/lang/String;)I", reinterpret_cast<void*>(jni_nativeAddTrack)},
    {"nativeRemoveTrack", "(I)V", reinterpret_cast<void*>(jni_nativeRemoveTrack)},
    {"nativeSetTrackVolume", "(IF)V", reinterpret_cast<void*>(jni_nativeSetTrackVolume)},
    {"nativeSetTrackPan", "(IF)V", reinterpret_cast<void*>(jni_nativeSetTrackPan)},
    {"nativeSetTrackMute", "(IZ)V", reinterpret_cast<void*>(jni_nativeSetTrackMute)},
    {"nativeSetTrackSolo", "(IZ)V", reinterpret_cast<void*>(jni_nativeSetTrackSolo)},
    {"nativeSetTrackArmed", "(IZ)V", reinterpret_cast<void*>(jni_nativeSetTrackArmed)},
    {"nativeSetTrackEqBand", "(IIFFFF)V", reinterpret_cast<void*>(jni_nativeSetTrackEqBand)},
    {"nativeSetTrackDelay", "(IFFF)V", reinterpret_cast<void*>(jni_nativeSetTrackDelay)},
    {"nativeSetMasterLimiter", "(FFF)V", reinterpret_cast<void*>(jni_nativeSetMasterLimiter)},
    {"nativeGetPlaybackPositionFrames", "()J", reinterpret_cast<void*>(jni_nativeGetPlaybackPositionFrames)},
    {"nativeGetMeteringData", "(Ljava/nio/ByteBuffer;I)V", reinterpret_cast<void*>(jni_nativeGetMeteringData)},
    {"nativeLoadClip", "(IILjava/lang/String;J)Z", reinterpret_cast<void*>(jni_nativeLoadClip)},
    {"nativeStartRecording", "(ILjava/lang/String;)Z", reinterpret_cast<void*>(jni_nativeStartRecording)},
    {"nativeStopRecording", "(I)V", reinterpret_cast<void*>(jni_nativeStopRecording)},
    {"nativeRenderMixdown", "(Ljava/lang/String;IJ)Z", reinterpret_cast<void*>(jni_nativeRenderMixdown)}
};

static const char* const kNativeClassName = "com/android/daw/bridge/NativeAudioEngine";

// =============================================================================
// JNI_OnLoad and JNI_OnUnload
// =============================================================================

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        LOGE("JNI_OnLoad: GetEnv failed");
        return JNI_ERR;
    }

    jclass clazz = env->FindClass(kNativeClassName);
    if (clazz) {
        const jint method_count = sizeof(g_native_methods) / sizeof(g_native_methods[0]);
        if (env->RegisterNatives(clazz, g_native_methods, method_count) < 0) {
            LOGW("JNI_OnLoad: RegisterNatives signature mismatch; dynamic JNI exports will be utilized.");
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
            }
        } else {
            LOGI("JNI_OnLoad: Successfully registered %d native methods for %s", method_count, kNativeClassName);
        }
    } else {
        LOGW("JNI_OnLoad: Class %s lookup deferred.", kNativeClassName);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
    }
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* /*reserved*/) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) {
        jclass clazz = env->FindClass(kNativeClassName);
        if (clazz) {
            env->UnregisterNatives(clazz);
        }
    }
}

// =============================================================================
// Export Declarations for Direct Symbol Resolution
// =============================================================================

extern "C" {

JNIEXPORT void JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeInit(JNIEnv* env, jobject thiz, jint sampleRate, jint framesPerBurst) {
    jni_nativeInit(env, thiz, sampleRate, framesPerBurst);
}

JNIEXPORT jboolean JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeStart(JNIEnv* /*env*/, jobject /*thiz*/) {
    return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeRelease(JNIEnv* env, jobject thiz) {
    jni_nativeRelease(env, thiz);
}

JNIEXPORT void JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativePlay(JNIEnv* env, jobject thiz) {
    jni_nativePlay(env, thiz);
}

JNIEXPORT void JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativePause(JNIEnv* env, jobject thiz) {
    jni_nativePause(env, thiz);
}

JNIEXPORT void JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeStop(JNIEnv* env, jobject thiz) {
    jni_nativeStop(env, thiz);
}

JNIEXPORT void JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeStopTransport(JNIEnv* env, jobject thiz) {
    jni_nativeStop(env, thiz);
}

JNIEXPORT void JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeSeek(JNIEnv* env, jobject thiz, jlong framePosition) {
    jni_nativeSeek(env, thiz, framePosition);
}

JNIEXPORT void JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeSeekTo(JNIEnv* env, jobject thiz, jlong framePosition) {
    jni_nativeSeek(env, thiz, framePosition);
}

JNIEXPORT void JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeSetLoop(JNIEnv* env, jobject thiz, jboolean enabled, jlong startFrame, jlong endFrame) {
    jni_nativeSetLoop(env, thiz, enabled, startFrame, endFrame);
}

JNIEXPORT void JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeSetTempo(JNIEnv* env, jobject thiz, jdouble bpm) {
    jni_nativeSetTempo(env, thiz, bpm);
}

JNIEXPORT jint JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeAddTrack(JNIEnv* env, jobject thiz, jstring name) {
    return jni_nativeAddTrack(env, thiz, name);
}

JNIEXPORT void JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeRemoveTrack(JNIEnv* env, jobject thiz, jint trackId) {
    jni_nativeRemoveTrack(env, thiz, trackId);
}

JNIEXPORT void JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeSetTrackVolume(JNIEnv* env, jobject thiz, jint trackId, jfloat linearGain) {
    jni_nativeSetTrackVolume(env, thiz, trackId, linearGain);
}

JNIEXPORT void JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeSetTrackPan(JNIEnv* env, jobject thiz, jint trackId, jfloat panPosition) {
    jni_nativeSetTrackPan(env, thiz, trackId, panPosition);
}

JNIEXPORT void JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeSetTrackMute(JNIEnv* env, jobject thiz, jint trackId, jboolean muted) {
    jni_nativeSetTrackMute(env, thiz, trackId, muted);
}

JNIEXPORT void JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeSetTrackSolo(JNIEnv* env, jobject thiz, jint trackId, jboolean soloed) {
    jni_nativeSetTrackSolo(env, thiz, trackId, soloed);
}

JNIEXPORT void JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeSetTrackArmed(JNIEnv* env, jobject thiz, jint trackId, jboolean armed) {
    jni_nativeSetTrackArmed(env, thiz, trackId, armed);
}

JNIEXPORT void JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeSetTrackEqBand(JNIEnv* env, jobject thiz, jint trackId, jint bandIndex, jint filterType, jfloat freqHz, jfloat gainDb, jfloat qFactor) {
    jni_nativeSetTrackEqBand(env, thiz, trackId, bandIndex, filterType, freqHz, gainDb, qFactor);
}

JNIEXPORT void JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeSetTrackEq(JNIEnv* env, jobject thiz, jint trackId, jint bandIndex, jint filterType, jfloat freqHz, jfloat qFactor, jfloat gainDb) {
    jni_nativeSetTrackEqBand(env, thiz, trackId, bandIndex, filterType, freqHz, gainDb, qFactor);
}

JNIEXPORT void JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeSetTrackDelay(JNIEnv* env, jobject thiz, jint trackId, jfloat delayMs, jfloat feedback, jfloat wetDry) {
    jni_nativeSetTrackDelay(env, thiz, trackId, delayMs, feedback, wetDry);
}

JNIEXPORT void JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeSetMasterLimiter(JNIEnv* env, jobject thiz, jfloat thresholdDb, jfloat ceilingDb, jfloat releaseMs) {
    jni_nativeSetMasterLimiter(env, thiz, thresholdDb, ceilingDb, releaseMs);
}

JNIEXPORT jlong JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeGetPlaybackPositionFrames(JNIEnv* env, jobject thiz) {
    return jni_nativeGetPlaybackPositionFrames(env, thiz);
}

JNIEXPORT jlong JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeGetPlayheadFrame(JNIEnv* env, jobject thiz) {
    return jni_nativeGetPlaybackPositionFrames(env, thiz);
}

JNIEXPORT void JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeGetMeteringData(JNIEnv* env, jobject thiz, jobject directFloatBuffer, jint numTracks) {
    jni_nativeGetMeteringData(env, thiz, directFloatBuffer, numTracks);
}

JNIEXPORT jboolean JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeLoadClip(JNIEnv* env, jobject thiz, jint trackId, jint clipId, jstring wavFilePath, jlong startOffsetFrames) {
    return jni_nativeLoadClip(env, thiz, trackId, clipId, wavFilePath, startOffsetFrames);
}

JNIEXPORT jboolean JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeRemoveClip(JNIEnv* /*env*/, jobject /*thiz*/, jint /*trackId*/, jint /*clipId*/) {
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeStartRecording(JNIEnv* env, jobject thiz, jint trackId, jstring destinationWavPath) {
    return jni_nativeStartRecording(env, thiz, trackId, destinationWavPath);
}

JNIEXPORT void JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeStopRecording(JNIEnv* env, jobject thiz, jint trackId) {
    jni_nativeStopRecording(env, thiz, trackId);
}

JNIEXPORT jboolean JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeRenderMixdown(JNIEnv* env, jobject thiz, jstring outputWavPath, jint bitDepth, jlong totalFrames) {
    return jni_nativeRenderMixdown(env, thiz, outputWavPath, bitDepth, totalFrames);
}

JNIEXPORT jboolean JNICALL Java_com_android_daw_bridge_NativeAudioEngine_nativeRenderOffline(JNIEnv* env, jobject thiz, jstring outputWavPath, jlong totalFrames) {
    return jni_nativeRenderMixdown(env, thiz, outputWavPath, 16, totalFrames);
}

} // extern "C"

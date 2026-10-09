#ifndef ANDROID_DAW_JNI_BRIDGE_H
#define ANDROID_DAW_JNI_BRIDGE_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

// JNI Lifecycle callbacks
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved);
JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved);

#ifdef __cplusplus
}
#endif

#endif // ANDROID_DAW_JNI_BRIDGE_H

#include <jni.h>
#include <string>

#include "aaudio_sink.h"

// Phase 0: minimal JNI call to validate the CMake/NDK toolchain end to end.
extern "C" JNIEXPORT jstring JNICALL
Java_com_davidpallier_lumiomusic_audionative_NativeLibraryLoader_nativePing(
        JNIEnv *env, jobject /* this */) {
    std::string result = "lumionative-ok";
    return env->NewStringUTF(result.c_str());
}

// Phase 8 feasibility spike: JNI bridge for the best-effort AAudio exclusive-mode sink.
// See AudioNativeSink.kt for the Kotlin-side API and usage notes.

extern "C" JNIEXPORT jlong JNICALL
Java_com_davidpallier_lumiomusic_audionative_AudioNativeSink_nativeOpen(
        JNIEnv *env, jobject /* this */, jint sampleRateHz, jint channelCount, jint format) {
    AAudioSinkHandle *handle = aaudio_sink_open(sampleRateHz, channelCount, format);
    return reinterpret_cast<jlong>(handle);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_davidpallier_lumiomusic_audionative_AudioNativeSink_nativeIsExclusive(
        JNIEnv *env, jobject /* this */, jlong handle) {
    return aaudio_sink_is_exclusive(reinterpret_cast<AAudioSinkHandle *>(handle));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_davidpallier_lumiomusic_audionative_AudioNativeSink_nativeWrite(
        JNIEnv *env, jobject /* this */, jlong handle, jbyteArray buffer, jint numFrames,
        jlong timeoutNanos) {
    jbyte *bytes = env->GetByteArrayElements(buffer, nullptr);
    if (bytes == nullptr) return -1;
    int32_t written = aaudio_sink_write(
            reinterpret_cast<AAudioSinkHandle *>(handle), bytes, numFrames, timeoutNanos);
    env->ReleaseByteArrayElements(buffer, bytes, JNI_ABORT);
    return written;
}

extern "C" JNIEXPORT void JNICALL
Java_com_davidpallier_lumiomusic_audionative_AudioNativeSink_nativeClose(
        JNIEnv *env, jobject /* this */, jlong handle) {
    aaudio_sink_close(reinterpret_cast<AAudioSinkHandle *>(handle));
}

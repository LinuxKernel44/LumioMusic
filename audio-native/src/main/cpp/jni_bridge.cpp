#include <jni.h>
#include <string>

// Phase 0: minimal JNI call to validate the CMake/NDK toolchain end to end.
// The real AAudio exclusive-mode sink is added in Phase 8.
extern "C" JNIEXPORT jstring JNICALL
Java_com_davidpallier_lumiomusic_audionative_NativeLibraryLoader_nativePing(
        JNIEnv *env, jobject /* this */) {
    std::string result = "lumionative-ok";
    return env->NewStringUTF(result.c_str());
}

#include "aaudio_sink.h"

#include <aaudio/AAudio.h>
#include <android/log.h>

#define LOG_TAG "LumioAAudioSink"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

struct AAudioSinkHandle {
    AAudioStream *stream = nullptr;
};

namespace {

AAudioStream *openWithSharingMode(int32_t sampleRateHz, int32_t channelCount, int32_t format,
                                   aaudio_sharing_mode_t sharingMode, aaudio_result_t *outResult) {
    AAudioStreamBuilder *builder = nullptr;
    if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK || builder == nullptr) {
        *outResult = AAUDIO_ERROR_INTERNAL;
        return nullptr;
    }

    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setSampleRate(builder, sampleRateHz);
    AAudioStreamBuilder_setChannelCount(builder, channelCount);
    AAudioStreamBuilder_setFormat(builder, static_cast<aaudio_format_t>(format));
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder, sharingMode);
    AAudioStreamBuilder_setUsage(builder, AAUDIO_USAGE_MEDIA);
    AAudioStreamBuilder_setContentType(builder, AAUDIO_CONTENT_TYPE_MUSIC);

    AAudioStream *stream = nullptr;
    *outResult = AAudioStreamBuilder_openStream(builder, &stream);
    AAudioStreamBuilder_delete(builder);
    return (*outResult == AAUDIO_OK) ? stream : nullptr;
}

}  // namespace

extern "C" {

AAudioSinkHandle *aaudio_sink_open(int32_t sampleRateHz, int32_t channelCount, int32_t format) {
    aaudio_result_t result;
    AAudioStream *stream = openWithSharingMode(
            sampleRateHz, channelCount, format, AAUDIO_SHARING_MODE_EXCLUSIVE, &result);

    if (stream == nullptr) {
        LOGW("Exclusive-mode open failed (%s); falling back to shared mode",
             AAudio_convertResultToText(result));
        stream = openWithSharingMode(
                sampleRateHz, channelCount, format, AAUDIO_SHARING_MODE_SHARED, &result);
    }

    if (stream == nullptr) {
        LOGW("Shared-mode open also failed (%s)", AAudio_convertResultToText(result));
        return nullptr;
    }

    // Mandatory check even on a "successful" exclusive-mode open: some devices silently
    // grant shared mode instead of honoring the exclusive request or failing.
    aaudio_sharing_mode_t granted = AAudioStream_getSharingMode(stream);
    LOGI("AAudio stream opened: sharingMode=%s",
         granted == AAUDIO_SHARING_MODE_EXCLUSIVE ? "EXCLUSIVE" : "SHARED");

    result = AAudioStream_requestStart(stream);
    if (result != AAUDIO_OK) {
        LOGW("requestStart failed (%s)", AAudio_convertResultToText(result));
        AAudioStream_close(stream);
        return nullptr;
    }

    auto *handle = new AAudioSinkHandle();
    handle->stream = stream;
    return handle;
}

bool aaudio_sink_is_exclusive(AAudioSinkHandle *handle) {
    if (handle == nullptr || handle->stream == nullptr) return false;
    return AAudioStream_getSharingMode(handle->stream) == AAUDIO_SHARING_MODE_EXCLUSIVE;
}

int32_t aaudio_sink_write(AAudioSinkHandle *handle, const void *buffer, int32_t numFrames,
                           int64_t timeoutNanos) {
    if (handle == nullptr || handle->stream == nullptr) return AAUDIO_ERROR_INVALID_STATE;
    return AAudioStream_write(handle->stream, buffer, numFrames, timeoutNanos);
}

void aaudio_sink_close(AAudioSinkHandle *handle) {
    if (handle == nullptr) return;
    if (handle->stream != nullptr) {
        AAudioStream_requestStop(handle->stream);
        AAudioStream_close(handle->stream);
    }
    delete handle;
}

}  // extern "C"

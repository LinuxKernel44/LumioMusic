#pragma once

#include <cstdint>

struct AAudioSinkHandle;

extern "C" {

/**
 * Opens an AAudio output stream, requesting exclusive sharing mode first. Per the project's
 * hi-res plan, this always falls back to shared mode rather than failing outright: some devices
 * reject an exclusive request, others silently grant shared instead - callers must check
 * aaudio_sink_is_exclusive() after a successful open, never assume the request was honored.
 * format follows AAudio's aaudio_format_t (PCM_I16 = 1, PCM_FLOAT = 2). Returns null on failure.
 */
AAudioSinkHandle *aaudio_sink_open(int32_t sampleRateHz, int32_t channelCount, int32_t format);

bool aaudio_sink_is_exclusive(AAudioSinkHandle *handle);

/** Blocking write of PCM frames (not bytes); returns frames written or a negative AAudio error. */
int32_t aaudio_sink_write(AAudioSinkHandle *handle, const void *buffer, int32_t numFrames, int64_t timeoutNanos);

void aaudio_sink_close(AAudioSinkHandle *handle);

}

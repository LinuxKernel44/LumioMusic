package com.davidpallier.lumiomusic.playback.audio.flac

import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.decoder.CryptoConfig
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DecoderAudioRenderer

/**
 * Decodes FLAC with [FlacDecoder] instead of the platform codec, so 24-bit material keeps all its
 * bits. Registered ahead of `MediaCodecAudioRenderer` (see `PlaybackModule`), so it wins for every
 * FLAC track it can handle; anything else (MP3, AAC, ...) is untouched.
 */
class FlacAudioRenderer(
    eventHandler: Handler?,
    eventListener: AudioRendererEventListener?,
    audioSink: AudioSink
) : DecoderAudioRenderer<FlacDecoder>(eventHandler, eventListener, audioSink) {

    override fun getName(): String = "KotlinFlacAudioRenderer"

    override fun supportsFormatInternal(format: Format): Int {
        if (format.sampleMimeType != MimeTypes.AUDIO_FLAC || format.initializationData.isEmpty()) {
            return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
        }
        val info = try {
            FlacStreamInfo.parse(format.initializationData[0])
        } catch (e: FlacFormatException) {
            return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE)
        }
        if (!sinkSupportsFormat(FlacDecoder.outputFormatFor(info))) {
            return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE)
        }
        return RendererCapabilities.create(C.FORMAT_HANDLED)
    }

    override fun createDecoder(format: Format, cryptoConfig: CryptoConfig?): FlacDecoder = FlacDecoder(format.initializationData)

    override fun getOutputFormat(decoder: FlacDecoder): Format = decoder.outputFormat
}

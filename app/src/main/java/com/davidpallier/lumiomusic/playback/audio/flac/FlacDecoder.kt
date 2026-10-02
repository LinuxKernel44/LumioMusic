package com.davidpallier.lumiomusic.playback.audio.flac

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoder
import androidx.media3.decoder.SimpleDecoderOutputBuffer
import androidx.media3.decoder.DecoderException

class FlacDecoderException(message: String, cause: Throwable? = null) : DecoderException(message, cause)

/** Media3 [SimpleDecoder] around [FlacFrameDecoder]: one extractor sample in, interleaved int PCM out. */
class FlacDecoder(initializationData: List<ByteArray>) : SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, FlacDecoderException>(
    arrayOf(DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL)),
    arrayOf(SimpleDecoderOutputBuffer { })
) {
    val streamInfo: FlacStreamInfo = try {
        FlacStreamInfo.parse(initializationData.firstOrNull() ?: throw FlacFormatException("Missing STREAMINFO"))
    } catch (e: FlacFormatException) {
        throw FlacDecoderException("Invalid FLAC initialization data", e)
    }

    private val frameDecoder = FlacFrameDecoder(streamInfo)
    private var inputScratch = ByteArray(streamInfo.maxBlockSize * 4)

    init {
        setInitialInputBufferSize(maxOf(streamInfo.maxBlockSize * streamInfo.channels * 4, 32 * 1024))
    }

    val outputFormat: Format = outputFormatFor(streamInfo)

    override fun getName(): String = "KotlinFlacDecoder"

    override fun createInputBuffer(): DecoderInputBuffer = DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL)

    override fun createOutputBuffer(): SimpleDecoderOutputBuffer = SimpleDecoderOutputBuffer(this::releaseOutputBuffer)

    override fun createUnexpectedDecodeException(error: Throwable): FlacDecoderException =
        FlacDecoderException("Unexpected decode error", error)

    override fun decode(inputBuffer: DecoderInputBuffer, outputBuffer: SimpleDecoderOutputBuffer, reset: Boolean): FlacDecoderException? {
        val data = inputBuffer.data ?: return null
        val length = data.remaining()
        if (inputScratch.size < length) inputScratch = ByteArray(length)
        data.get(inputScratch, 0, length)

        frameDecoder.resetOutput()
        try {
            frameDecoder.decode(inputScratch, 0, length)
        } catch (e: FlacFormatException) {
            return FlacDecoderException("Corrupt FLAC frame", e)
        }
        val pcm = frameDecoder.output
        pcm.flip()
        val out = outputBuffer.init(inputBuffer.timeUs, pcm.remaining())
        out.put(pcm)
        out.flip() // Media3 reads [position, limit): leave it positioned at the start
        return null
    }

    companion object {
        fun outputFormatFor(info: FlacStreamInfo): Format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(info.channels)
            .setSampleRate(info.sampleRate)
            .setPcmEncoding(
                when (info.outputBits) {
                    16 -> C.ENCODING_PCM_16BIT
                    24 -> C.ENCODING_PCM_24BIT
                    else -> C.ENCODING_PCM_32BIT
                }
            )
            .build()
    }
}

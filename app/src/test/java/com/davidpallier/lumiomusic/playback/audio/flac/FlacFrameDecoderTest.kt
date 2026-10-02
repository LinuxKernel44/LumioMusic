package com.davidpallier.lumiomusic.playback.audio.flac

import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decodes real files made with the reference `flac` encoder (synthetic sine + noise, silence,
 * full-scale bursts; 16/24-bit, 44.1-192 kHz, mono/stereo/6ch, several encoder modes) and checks
 * the output against the MD5 of the original samples that the encoder stores in STREAMINFO.
 * A matching MD5 means the decode is bit-exact.
 */
class FlacFrameDecoderTest {

    private class Parsed(val info: FlacStreamInfo, val audio: ByteArray, val audioOffset: Int)

    private fun load(name: String): Parsed {
        val data = javaClass.getResourceAsStream("/flac/$name.flac")!!.readBytes()
        assertEquals("fLaC", String(data, 0, 4))
        var position = 4
        var info: FlacStreamInfo? = null
        while (true) {
            val header = data[position].toInt() and 0xFF
            val last = header and 0x80 != 0
            val type = header and 0x7F
            val length = ((data[position + 1].toInt() and 0xFF) shl 16) or
                ((data[position + 2].toInt() and 0xFF) shl 8) or (data[position + 3].toInt() and 0xFF)
            if (type == 0) info = FlacStreamInfo.parse(data.copyOfRange(position + 4, position + 4 + length))
            position += 4 + length
            if (last) break
        }
        return Parsed(info!!, data, position)
    }

    private fun assertBitExact(name: String, expectedBits: Int, expectedChannels: Int, expectedRate: Int) {
        val file = load(name)
        assertEquals(expectedBits, file.info.outputBits)
        assertEquals(expectedChannels, file.info.channels)
        assertEquals(expectedRate, file.info.sampleRate)

        val decoder = FlacFrameDecoder(file.info)
        val frames = decoder.decode(file.audio, file.audioOffset, file.audio.size - file.audioOffset)
        assertTrue("expected several frames in $name, got $frames", frames > 1)

        val pcm = decoder.output
        pcm.flip()
        val bytes = ByteArray(pcm.remaining()).also { pcm.get(it) }
        val expectedBytes = file.info.totalSamples * expectedChannels * (expectedBits / 8)
        assertEquals("decoded length of $name", expectedBytes, bytes.size.toLong())
        assertArrayEquals("MD5 of $name", file.info.md5, MessageDigest.getInstance("MD5").digest(bytes))
    }

    @Test fun `16-bit 44_1 kHz stereo is bit exact`() = assertBitExact("cd_16_44k_stereo", 16, 2, 44100)
    @Test fun `24-bit 96 kHz stereo is bit exact`() = assertBitExact("hires_24_96k_stereo", 24, 2, 96000)
    @Test fun `24-bit 192 kHz mono is bit exact`() = assertBitExact("hires_24_192k_mono", 24, 1, 192000)
    @Test fun `fixed predictors and verbatim blocks are bit exact`() = assertBitExact("fixed_16_48k_stereo", 16, 2, 48000)
    @Test fun `six channels are bit exact`() = assertBitExact("six_ch_16_48k", 16, 6, 48000)
    @Test fun `mid side 24-bit 88_2 kHz is bit exact`() = assertBitExact("midside_24_88k", 24, 2, 88200)

    @Test fun `frames can also be fed one buffer at a time`() {
        val file = load("hires_24_96k_stereo")
        val whole = FlacFrameDecoder(file.info).also { it.decode(file.audio, file.audioOffset, file.audio.size - file.audioOffset) }
        // Split exactly like Media3's extractor would: re-find each frame start by decoding sequentially.
        val oneByOne = FlacFrameDecoder(file.info)
        var offset = file.audioOffset
        val probe = FlacFrameDecoder(file.info)
        while (offset < file.audio.size) {
            // Decode a single frame from the remaining bytes to learn its length: decode() stops
            // at the first frame when given exactly that frame, so grow the window until it matches.
            var length = 2
            var consumedFrames: Int
            while (true) {
                probe.resetOutput()
                try {
                    consumedFrames = probe.decode(file.audio, offset, length)
                    if (consumedFrames == 1 && offset + length == file.audio.size ||
                        (offset + length < file.audio.size && isSync(file.audio, offset + length))
                    ) break
                } catch (_: FlacFormatException) {
                }
                length++
            }
            oneByOne.decode(file.audio, offset, length)
            offset += length
        }
        assertEquals(whole.output.position(), oneByOne.output.position())
        assertArrayEquals(whole.output.array().copyOf(whole.output.position()), oneByOne.output.array().copyOf(oneByOne.output.position()))
    }

    private fun isSync(data: ByteArray, at: Int) =
        (data[at].toInt() and 0xFF) == 0xFF && (data[at + 1].toInt() and 0xFE) == 0xF8

    @Test fun `streaminfo parses the extractor's initialization data layout`() {
        val file = load("cd_16_44k_stereo")
        val raw = file.audio.copyOfRange(4, 4 + 4 + 34) // block header + STREAMINFO
        val withMarker = "fLaC".toByteArray() + raw
        val parsed = FlacStreamInfo.parse(withMarker)
        assertEquals(44100, parsed.sampleRate)
        assertEquals(16, parsed.bitsPerSample)
    }
}

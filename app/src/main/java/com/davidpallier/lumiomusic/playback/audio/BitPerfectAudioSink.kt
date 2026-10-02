package com.davidpallier.lumiomusic.playback.audio

import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/**
 * A minimal PCM-only [AudioSink] that writes straight to an [AudioTrack] in exactly the format a
 * USB DAC's bit-perfect mixer advertised (see [BitPerfectOutputManager]). It exists because
 * `DefaultAudioSink` always narrows 24/32-bit/float PCM to 16 bits or float, neither of which a
 * bit-perfect USB route accepts.
 *
 * Deliberately not supported (never needed for local lossless playback): offload, tunneling,
 * passthrough, speed changes, silence skipping, aux effects.
 *
 * Seeks ([flush]) release the AudioTrack and lazily create a new one, so a fresh playback head
 * position always starts at zero instead of relying on per-device flush behavior.
 */
class BitPerfectAudioSink(
    private val outputManager: BitPerfectOutputManager
) : AudioSink {

    private class Config(val inputFormat: Format, val plan: BitPerfectPlan) {
        val inputEncoding = inputFormat.pcmEncoding
        val outputEncoding = plan.output.encoding
        val sampleRate = plan.output.sampleRate
        val channels = plan.output.channelCount
        val outputFrameBytes = PcmConverter.bytesPerSample(outputEncoding) * channels
        val inputFrameBytes = PcmConverter.bytesPerSample(inputEncoding) * channels

        fun sameOutputAs(other: Config) =
            plan.device.id == other.plan.device.id && plan.output == other.plan.output &&
                plan.channelMask == other.plan.channelMask
    }

    private class Checkpoint(val framesBase: Long, val startMediaTimeUs: Long)

    private var listener: AudioSink.Listener? = null
    private var config: Config? = null
    private var pendingConfig: Config? = null
    private var track: AudioTrack? = null
    private var trackConfig: Config? = null

    private var outBuffer: ByteBuffer = ByteBuffer.allocateDirect(INITIAL_OUT_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    private var playing = false
    private var volume = 1f
    private var audioSessionId = C.AUDIO_SESSION_ID_UNSET
    private var handledEndOfStream = false
    private var needsSync = true
    private var positionAdvancingNotified = false

    private var writtenFrames = 0L
    private var lastHeadLow = 0L
    private var headWraps = 0L
    private val checkpoints = ArrayDeque<Checkpoint>()
    private var expectedNextPtsUs = C.TIME_UNSET
    private val timestamp = AudioTimestamp()
    private var playingSinceMs = 0L
    private var rateCheckStartMs = 0L
    private var rateCheckStartFrames = 0L
    private var rateCheckDone = false
    private var lastDiagLogMs = 0L

    // region configuration

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
    }

    override fun supportsFormat(format: Format): Boolean = getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun getFormatSupport(format: Format): Int {
        if (format.sampleMimeType != androidx.media3.common.MimeTypes.AUDIO_RAW) return AudioSink.SINK_FORMAT_UNSUPPORTED
        return when (format.pcmEncoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT ->
                AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
            else -> AudioSink.SINK_FORMAT_UNSUPPORTED
        }
    }

    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport = AudioOffloadSupport.DEFAULT_UNSUPPORTED

    @Throws(AudioSink.ConfigurationException::class)
    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        val decision = outputManager.decide(inputFormat)
        val plan = (decision as? BitPerfectDecision.Use)?.plan
            ?: throw AudioSink.ConfigurationException("Bit-perfect output unavailable: $decision", inputFormat)
        val newConfig = Config(inputFormat, plan)

        val current = trackConfig
        when {
            track != null && current != null && current.sameOutputAs(newConfig) -> {
                config = newConfig
                pendingConfig = null
            }
            track != null && hasPendingData() -> pendingConfig = newConfig // finish playing the old format first
            else -> {
                releaseTrack()
                config = newConfig
                pendingConfig = null
            }
        }
    }

    // endregion

    // region data path

    @Throws(AudioSink.InitializationException::class, AudioSink.WriteException::class)
    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        pendingConfig?.let { next ->
            if (hasPendingData()) {
                writePendingOutput()
                return false
            }
            releaseTrack()
            config = next
            pendingConfig = null
        }
        val cfg = config ?: throw IllegalStateException("handleBuffer before configure")
        if (track == null) initializeTrack(cfg)

        checkForStall(cfg)
        if (outBuffer.hasRemaining()) {
            writePendingOutput()
            if (outBuffer.hasRemaining()) return false
        }
        if (!buffer.hasRemaining()) return true

        val inputFrames = buffer.remaining() / cfg.inputFrameBytes
        if (inputFrames == 0) {
            buffer.position(buffer.limit())
            return true
        }

        val outputBytes = inputFrames * cfg.outputFrameBytes
        if (outBuffer.capacity() < outputBytes) {
            outBuffer = ByteBuffer.allocateDirect(outputBytes).order(ByteOrder.LITTLE_ENDIAN)
        }

        if (!needsSync && expectedNextPtsUs != C.TIME_UNSET &&
            kotlin.math.abs(expectedNextPtsUs - presentationTimeUs) > PTS_JUMP_US
        ) {
            needsSync = true
        }
        if (needsSync) {
            checkpoints.addLast(Checkpoint(writtenFrames, presentationTimeUs))
            needsSync = false
        }
        expectedNextPtsUs = presentationTimeUs + inputFrames * 1_000_000L / cfg.sampleRate

        outBuffer.clear()
        PcmConverter.convert(buffer, cfg.inputEncoding, outBuffer, cfg.outputEncoding, volume)
        outBuffer.flip()
        writePendingOutput()
        return true
    }

    /**
     * A bit-perfect route that accepts data but never plays it is worse than useless (silence), so
     * if the track is playing, has data, and has consumed nothing after [STALL_MS], give up: the
     * router catches this and falls back to the regular Android mixer.
     */
    private fun checkForStall(cfg: Config) {
        val t = track ?: return
        if (!playing || writtenFrames == 0L) {
            playingSinceMs = 0
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (playingSinceMs == 0L) playingSinceMs = now
        val consumed = consumedFrames(t)
        if (now - lastDiagLogMs > DIAG_LOG_MS) {
            lastDiagLogMs = now
            Log.i(TAG, "track: playState=${t.playState} written=$writtenFrames consumed=$consumed " +
                "underruns=${t.underrunCount} routed=${t.routedDevice?.productName} " +
                "format=${cfg.plan.output} bufferFrames=${t.bufferSizeInFrames}")
        }
        checkConsumptionRate(t, cfg, consumed, now)
        if (consumed == 0L && now - playingSinceMs > STALL_MS) {
            Log.w(TAG, "AudioTrack consumed nothing for ${now - playingSinceMs} ms (written=$writtenFrames) - giving up on bit-perfect")
            throw AudioSink.InitializationException(
                "Bit-perfect AudioTrack stalled", t.playState, cfg.inputFormat, false, null
            )
        }
    }

    /**
     * A track can never legitimately drain faster than its sample rate. On the OnePlus 15 the HAL
     * configures the USB backend with 32-bit slots for 24-bit audio while the track writes packed
     * 3-byte samples, so the stream drains exactly 4/3 too fast and the DAC gets misaligned data.
     * Anything clearly faster than real time means the format isn't understood end to end.
     */
    private fun checkConsumptionRate(t: AudioTrack, cfg: Config, consumed: Long, now: Long) {
        if (rateCheckDone || consumed == 0L) return
        if (rateCheckStartMs == 0L) {
            rateCheckStartMs = now
            rateCheckStartFrames = consumed
            return
        }
        val elapsedMs = now - rateCheckStartMs
        if (elapsedMs < RATE_CHECK_MS) return
        rateCheckDone = true
        val expectedFrames = cfg.sampleRate * elapsedMs / 1000.0
        val ratio = (consumed - rateCheckStartFrames) / expectedFrames
        Log.i(TAG, "consumption ratio vs nominal rate: %.3f over %d ms".format(ratio, elapsedMs))
        if (ratio > MAX_RATE_RATIO) {
            Log.w(TAG, "AudioTrack drains %.2fx faster than real time - the DAC is not reading this format as sent".format(ratio))
            throw AudioSink.InitializationException(
                "Bit-perfect stream drains too fast", t.playState, cfg.inputFormat, false, null
            )
        }
    }

    private fun initializeTrack(cfg: Config) {
        if (!outputManager.engage(cfg.plan)) {
            throw AudioSink.InitializationException(
                "Platform refused the bit-perfect mixer attributes", AudioTrack.STATE_UNINITIALIZED,
                cfg.inputFormat, /* isRecoverable= */ false, null
            )
        }
        val audioFormat = AudioFormat.Builder()
            .setSampleRate(cfg.sampleRate)
            .setChannelMask(cfg.plan.channelMask)
            .setEncoding(cfg.outputEncoding)
            .build()
        val minBuffer = AudioTrack.getMinBufferSize(cfg.sampleRate, cfg.plan.channelMask, cfg.outputEncoding)
        val bufferBytes = max(max(minBuffer, 0) * 2, cfg.outputFrameBytes * cfg.sampleRate * BUFFER_MS / 1000)
        val newTrack = try {
            AudioTrack.Builder()
                .setAudioAttributes(outputManager.audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .apply { if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) setSessionId(audioSessionId) }
                .build()
        } catch (e: Exception) {
            throw AudioSink.InitializationException(
                "Could not create the bit-perfect AudioTrack", AudioTrack.STATE_UNINITIALIZED,
                cfg.inputFormat, false, e
            )
        }
        if (newTrack.state != AudioTrack.STATE_INITIALIZED) {
            newTrack.release()
            throw AudioSink.InitializationException(
                "Bit-perfect AudioTrack failed to initialize", newTrack.state, cfg.inputFormat, false, null
            )
        }
        newTrack.setPreferredDevice(cfg.plan.device)
        Log.i(TAG, "AudioTrack ready: ${cfg.plan.output}, buffer=${bufferBytes}B, routed=${newTrack.routedDevice?.productName}")
        track = newTrack
        trackConfig = cfg
        resetCounters()
        playingSinceMs = 0
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) listener?.onAudioSessionIdChanged(newTrack.audioSessionId)
        if (playing) newTrack.play()
    }

    private fun writePendingOutput() {
        val t = track ?: return
        val cfg = trackConfig ?: return
        while (outBuffer.hasRemaining()) {
            val written = t.write(outBuffer, outBuffer.remaining(), AudioTrack.WRITE_NON_BLOCKING)
            if (written < 0) {
                outBuffer.position(outBuffer.limit()) // drop it; a dead track is caught by the stall check
                Log.w(TAG, "AudioTrack.write failed: $written (playState=${t.playState})")
                return
            }
            if (written == 0) return
            writtenFrames += written / cfg.outputFrameBytes
        }
    }

    @Throws(AudioSink.WriteException::class)
    override fun playToEndOfStream() {
        if (handledEndOfStream) return
        if (track == null) {
            handledEndOfStream = true
            return
        }
        if (outBuffer.hasRemaining()) {
            writePendingOutput()
            if (outBuffer.hasRemaining()) return
        }
        handledEndOfStream = true
    }

    override fun isEnded(): Boolean = handledEndOfStream && !hasPendingData()

    override fun hasPendingData(): Boolean {
        if (outBuffer.hasRemaining()) return true
        val t = track ?: return false
        return consumedFrames(t) < writtenFrames
    }

    // endregion

    // region position

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val t = track ?: return AudioSink.CURRENT_POSITION_NOT_SET
        val cfg = trackConfig ?: return AudioSink.CURRENT_POSITION_NOT_SET
        if (checkpoints.isEmpty()) return AudioSink.CURRENT_POSITION_NOT_SET

        val played = presentedFrames(t, cfg).coerceIn(0, writtenFrames)
        while (checkpoints.size > 1 && played >= checkpoints[1].framesBase) checkpoints.removeFirst()
        val checkpoint = checkpoints.first()
        if (played > 0 && !positionAdvancingNotified && playing) {
            positionAdvancingNotified = true
            listener?.onPositionAdvancing(SystemClock.elapsedRealtime())
        }
        return checkpoint.startMediaTimeUs + max(played - checkpoint.framesBase, 0) * 1_000_000L / cfg.sampleRate
    }

    /** Frames the DAC has actually presented (accurate while playing, frozen while paused). */
    private fun presentedFrames(t: AudioTrack, cfg: Config): Long {
        if (playing && t.getTimestamp(timestamp)) {
            val elapsedNs = System.nanoTime() - timestamp.nanoTime
            return timestamp.framePosition + elapsedNs * cfg.sampleRate / 1_000_000_000L
        }
        return consumedFrames(t)
    }

    /** Frames the track has consumed from our writes (monotonic, unwrapped 32-bit head position). */
    private fun consumedFrames(t: AudioTrack): Long {
        val head = t.playbackHeadPosition.toLong() and 0xFFFFFFFFL
        if (head < lastHeadLow) headWraps++
        lastHeadLow = head
        return (headWraps shl 32) + head
    }

    override fun handleDiscontinuity() {
        needsSync = true
    }

    // endregion

    // region lifecycle

    override fun play() {
        playing = true
        track?.play()
    }

    override fun pause() {
        playing = false
        track?.pause()
    }

    override fun flush() {
        releaseTrack()
        pendingConfig?.let {
            config = it
            pendingConfig = null
        }
        handledEndOfStream = false
    }

    override fun reset() {
        flush()
        config = null
        pendingConfig = null
        outputManager.disengage()
    }

    override fun release() = reset()

    private fun releaseTrack() {
        track?.let {
            try {
                it.pause()
                it.flush()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        track = null
        trackConfig = null
        outBuffer.clear().limit(0)
        resetCounters()
    }

    private fun resetCounters() {
        writtenFrames = 0
        lastHeadLow = 0
        headWraps = 0
        checkpoints.clear()
        needsSync = true
        expectedNextPtsUs = C.TIME_UNSET
        positionAdvancingNotified = false
        rateCheckStartMs = 0
        rateCheckStartFrames = 0
        rateCheckDone = false
    }

    // endregion

    // region settings (mostly unsupported; see class doc)

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) = Unit
    override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters.DEFAULT
    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) = Unit
    override fun getSkipSilenceEnabled(): Boolean = false
    override fun setAudioAttributes(audioAttributes: AudioAttributes) = Unit
    override fun getAudioAttributes(): AudioAttributes? = null
    override fun setAudioSessionId(audioSessionId: Int) {
        this.audioSessionId = audioSessionId
    }
    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) = Unit
    override fun getAudioTrackBufferSizeUs(): Long {
        if (trackConfig == null) return C.TIME_UNSET
        return BUFFER_MS * 1000L
    }
    override fun enableTunnelingV21() = Unit
    override fun disableTunneling() = Unit
    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
    }

    // endregion

    private companion object {
        const val TAG = "LumioHiRes"
        const val INITIAL_OUT_BYTES = 64 * 1024
        const val BUFFER_MS = 300
        const val PTS_JUMP_US = 200_000L
        const val STALL_MS = 2_500L
        const val RATE_CHECK_MS = 1_500L
        const val MAX_RATE_RATIO = 1.15
        const val DIAG_LOG_MS = 2_000L
    }
}

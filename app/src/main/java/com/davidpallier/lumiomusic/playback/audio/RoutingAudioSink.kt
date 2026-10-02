package com.davidpallier.lumiomusic.playback.audio

import android.media.AudioDeviceInfo
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.util.Clock
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioSink
import java.nio.ByteBuffer

/**
 * Sends each stream to either [standard] (`DefaultAudioSink`, float output - the baseline that
 * works everywhere) or [bitPerfect] (USB DAC, Android 14+). The standard sink is always the
 * default: bit-perfect is only chosen when [BitPerfectOutputManager.decide] says every condition
 * holds, and any failure to bring it up makes this router permanently fall back to [standard]
 * for the rest of the process rather than failing playback.
 *
 * Switching sinks mid-queue (e.g. a 96 kHz album followed by an unsupported 352.8 kHz one) waits
 * for the old sink to finish playing its buffered audio before the new one takes over.
 */
class RoutingAudioSink(
    private val standard: AudioSink,
    private val bitPerfect: AudioSink,
    private val outputManager: BitPerfectOutputManager,
    private val statusStore: AudioOutputStatusStore
) : AudioSink {

    private class ConfigureArgs(val format: Format, val bufferSize: Int, val outputChannels: IntArray?)
    private class Switch(val target: AudioSink, val args: ConfigureArgs)

    private var active: AudioSink = standard
    private var pendingSwitch: Switch? = null
    private var lastArgs: ConfigureArgs? = null
    private var bitPerfectFailed = false
    private var playing = false

    // State replayed onto whichever sink becomes active.
    private var listener: AudioSink.Listener? = null
    private var playerId: PlayerId? = null
    private var clock: Clock? = null
    private var audioAttributes: AudioAttributes? = null
    private var sessionId = C.AUDIO_SESSION_ID_UNSET
    private var auxEffectInfo: AuxEffectInfo? = null
    private var preferredDevice: AudioDeviceInfo? = null
    private var volume = 1f
    private var outputStreamOffsetUs = 0L

    private val listenerProxy = object : AudioSink.Listener {
        override fun onPositionDiscontinuity() { listener?.onPositionDiscontinuity() }
        override fun onPositionAdvancing(playoutStartSystemTimeMs: Long) { listener?.onPositionAdvancing(playoutStartSystemTimeMs) }
        override fun onUnderrun(bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
            listener?.onUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs)
        }
        override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) { listener?.onSkipSilenceEnabledChanged(skipSilenceEnabled) }
        override fun onOffloadBufferEmptying() { listener?.onOffloadBufferEmptying() }
        override fun onOffloadBufferFull() { listener?.onOffloadBufferFull() }
        override fun onAudioSinkError(audioSinkError: Exception) { listener?.onAudioSinkError(audioSinkError) }
        override fun onAudioCapabilitiesChanged() { listener?.onAudioCapabilitiesChanged() }
        override fun onSilenceSkipped() { listener?.onSilenceSkipped() }
        override fun onAudioSessionIdChanged(audioSessionId: Int) { listener?.onAudioSessionIdChanged(audioSessionId) }
        override fun onAudioTrackInitialized(audioTrackConfig: AudioSink.AudioTrackConfig) {
            listener?.onAudioTrackInitialized(audioTrackConfig)
        }
        override fun onAudioTrackReleased(audioTrackConfig: AudioSink.AudioTrackConfig) {
            listener?.onAudioTrackReleased(audioTrackConfig)
        }
    }

    init {
        standard.setListener(listenerProxy)
        bitPerfect.setListener(listenerProxy)
    }

    // region routing

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        val args = ConfigureArgs(inputFormat, specifiedBufferSize, outputChannels)
        lastArgs = args
        val target = chooseSink(inputFormat)

        if (target === active) {
            pendingSwitch = null
            active.configure(inputFormat, specifiedBufferSize, outputChannels)
        } else if (active.hasPendingData()) {
            pendingSwitch = Switch(target, args)
        } else {
            switchTo(target, args)
        }
    }

    private fun chooseSink(format: Format): AudioSink {
        if (format.sampleMimeType != androidx.media3.common.MimeTypes.AUDIO_RAW) {
            publishFallback(format, FallbackReason.SOURCE_FORMAT_UNSUPPORTED)
            return standard
        }
        if (bitPerfectFailed) {
            publishFallback(format, FallbackReason.DAC_REJECTED)
            return standard
        }
        return when (val decision = outputManager.decide(format)) {
            is BitPerfectDecision.Use -> {
                val out = decision.plan.output
                statusStore.publish(
                    AudioOutputStatus(OutputMode.BIT_PERFECT, out.sampleRate, out.bits ?: 0, isFloat = false)
                )
                bitPerfect
            }
            is BitPerfectDecision.Fallback -> {
                publishFallback(format, decision.reason)
                standard
            }
        }
    }

    private fun publishFallback(format: Format, reason: FallbackReason) {
        val isFloat = format.pcmEncoding == C.ENCODING_PCM_FLOAT
        statusStore.publish(
            AudioOutputStatus(
                mode = OutputMode.SYSTEM_MIXER,
                sampleRate = format.sampleRate,
                bitDepth = when (format.pcmEncoding) {
                    C.ENCODING_PCM_16BIT -> 16
                    C.ENCODING_PCM_24BIT -> 24
                    C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 32
                    else -> 0
                },
                isFloat = isFloat,
                fallbackReason = reason
            )
        )
    }

    private fun switchTo(target: AudioSink, args: ConfigureArgs) {
        if (target !== active) {
            Log.i(TAG, "Switching output: ${active.javaClass.simpleName} -> ${target.javaClass.simpleName}")
            active.flush()
            active.reset()
            active = target
            replayStateOnActive()
        }
        active.configure(args.format, args.bufferSize, args.outputChannels)
    }

    private fun replayStateOnActive() {
        playerId?.let { active.setPlayerId(it) }
        clock?.let { active.setClock(it) }
        audioAttributes?.let { active.setAudioAttributes(it) }
        if (sessionId != C.AUDIO_SESSION_ID_UNSET) active.setAudioSessionId(sessionId)
        auxEffectInfo?.let { active.setAuxEffectInfo(it) }
        preferredDevice?.let { active.setPreferredDevice(it) }
        active.setOutputStreamOffsetUs(outputStreamOffsetUs)
        active.setVolume(volume)
        if (playing) active.play()
    }

    private fun fallBackToStandard() {
        Log.w(TAG, "Bit-perfect output failed; falling back to the standard Android mixer for this session")
        bitPerfectFailed = true
        pendingSwitch = null
        val args = lastArgs ?: return
        publishFallback(args.format, FallbackReason.DAC_REJECTED)
        switchTo(standard, args)
    }

    @Throws(AudioSink.InitializationException::class, AudioSink.WriteException::class)
    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        pendingSwitch?.let { switch ->
            active.playToEndOfStream()
            if (!active.isEnded()) return false
            pendingSwitch = null
            switchTo(switch.target, switch.args)
        }
        return try {
            active.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        } catch (e: AudioSink.InitializationException) {
            if (active !== bitPerfect) throw e
            fallBackToStandard()
            active.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        }
    }

    override fun flush() {
        pendingSwitch?.let { switch ->
            pendingSwitch = null
            switchTo(switch.target, switch.args)
        }
        active.flush()
    }

    override fun reset() {
        pendingSwitch = null
        active.reset()
    }

    override fun release() {
        pendingSwitch = null
        standard.release()
        bitPerfect.release()
    }

    // endregion

    // region plain delegation + state caching

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
    }

    override fun setPlayerId(playerId: PlayerId?) {
        this.playerId = playerId
        standard.setPlayerId(playerId)
        bitPerfect.setPlayerId(playerId)
    }

    override fun setClock(clock: Clock) {
        this.clock = clock
        standard.setClock(clock)
        bitPerfect.setClock(clock)
    }

    // The standard sink runs with float output so 24-bit PCM stays lossless, which would make it
    // advertise float as directly supported - and MediaCodecAudioRenderer then asks platform codecs
    // for float output. Some of those (e.g. the FLAC/MP3 decoders on Android 12) ignore the request,
    // and playback ends up at the wrong speed. Report float as "needs transcoding" so codecs keep
    // emitting 16-bit; high-resolution FLAC goes through our own integer decoder instead.
    override fun supportsFormat(format: Format): Boolean = getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED
    override fun getFormatSupport(format: Format): Int =
        if (format.pcmEncoding == C.ENCODING_PCM_FLOAT) AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING
        else standard.getFormatSupport(format)
    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport = standard.getFormatOffloadSupport(format)
    override fun getAudioCapabilities(): AudioCapabilities? = standard.audioCapabilities

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long = active.getCurrentPositionUs(sourceEnded)

    override fun play() {
        playing = true
        active.play()
    }

    override fun pause() {
        playing = false
        active.pause()
    }

    override fun handleDiscontinuity() = active.handleDiscontinuity()

    @Throws(AudioSink.WriteException::class)
    override fun playToEndOfStream() = active.playToEndOfStream()

    override fun isEnded(): Boolean = pendingSwitch == null && active.isEnded
    override fun hasPendingData(): Boolean = active.hasPendingData()

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) = standard.setPlaybackParameters(playbackParameters)
    override fun getPlaybackParameters(): PlaybackParameters = standard.playbackParameters
    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) = standard.setSkipSilenceEnabled(skipSilenceEnabled)
    override fun getSkipSilenceEnabled(): Boolean = standard.skipSilenceEnabled

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        this.audioAttributes = audioAttributes
        standard.setAudioAttributes(audioAttributes)
        bitPerfect.setAudioAttributes(audioAttributes)
    }

    override fun getAudioAttributes(): AudioAttributes? = audioAttributes

    override fun setAudioSessionId(audioSessionId: Int) {
        sessionId = audioSessionId
        standard.setAudioSessionId(audioSessionId)
        bitPerfect.setAudioSessionId(audioSessionId)
    }

    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {
        this.auxEffectInfo = auxEffectInfo
        standard.setAuxEffectInfo(auxEffectInfo)
        bitPerfect.setAuxEffectInfo(auxEffectInfo)
    }

    override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) {
        preferredDevice = audioDeviceInfo
        standard.setPreferredDevice(audioDeviceInfo)
        bitPerfect.setPreferredDevice(audioDeviceInfo)
    }

    override fun setVirtualDeviceId(virtualDeviceId: Int) {
        standard.setVirtualDeviceId(virtualDeviceId)
        bitPerfect.setVirtualDeviceId(virtualDeviceId)
    }

    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
        this.outputStreamOffsetUs = outputStreamOffsetUs
        standard.setOutputStreamOffsetUs(outputStreamOffsetUs)
        bitPerfect.setOutputStreamOffsetUs(outputStreamOffsetUs)
    }

    override fun getAudioTrackBufferSizeUs(): Long = active.audioTrackBufferSizeUs

    override fun enableTunnelingV21() = standard.enableTunnelingV21()
    override fun disableTunneling() = standard.disableTunneling()
    override fun setOffloadMode(offloadMode: Int) = standard.setOffloadMode(offloadMode)
    override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) =
        standard.setOffloadDelayPadding(delayInFrames, paddingInFrames)
    override fun setAudioOutputProvider(audioOutputProvider: AudioOutputProvider) =
        standard.setAudioOutputProvider(audioOutputProvider)

    override fun setVolume(volume: Float) {
        this.volume = volume
        standard.setVolume(volume)
        bitPerfect.setVolume(volume)
    }

    // endregion

    private companion object {
        const val TAG = "LumioHiRes"
    }
}

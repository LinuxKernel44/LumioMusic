package com.davidpallier.lumiomusic.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import android.os.Handler
import com.davidpallier.lumiomusic.playback.audio.flac.FlacAudioRenderer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.davidpallier.lumiomusic.playback.audio.AudioOutputStatusStore
import com.davidpallier.lumiomusic.playback.audio.BitPerfectAudioSink
import com.davidpallier.lumiomusic.playback.audio.BitPerfectOutputManager
import com.davidpallier.lumiomusic.playback.audio.RoutingAudioSink
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The [ExoPlayer] instance lives for the process lifetime (a single [PlaybackService][
 * com.davidpallier.lumiomusic.playback.PlaybackService] attaches/detaches its MediaSession to
 * it as the service starts/stops) rather than being scoped to the service, which keeps queue
 * state alive across the service being briefly recreated by the system.
 */
@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {

    @Provides
    @Singleton
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        outputManager: BitPerfectOutputManager,
        statusStore: AudioOutputStatusStore
    ): ExoPlayer {
        // Float output keeps 24-bit sources lossless through the regular Android mixer; the router
        // swaps in the bit-perfect USB sink when a DAC is attached and every condition holds.
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                audioSink: AudioSink,
                eventHandler: Handler,
                eventListener: AudioRendererEventListener,
                out: ArrayList<Renderer>
            ) {
                // First in the list wins ties: our integer FLAC decoder before the platform codec.
                out.add(FlacAudioRenderer(eventHandler, eventListener, audioSink))
                super.buildAudioRenderers(
                    context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback,
                    audioSink, eventHandler, eventListener, out
                )
            }

            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                val standard = DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(true)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
                return RoutingAudioSink(standard, BitPerfectAudioSink(outputManager), outputManager, statusStore)
            }
        }
        return ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus= */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
}

package com.davidpallier.lumiomusic.playback.audio

import android.media.AudioFormat

/** A PCM format a USB DAC accepts through Android's bit-perfect mixer. */
data class OutputFormat(val sampleRate: Int, val channelCount: Int, val encoding: Int) {
    /** Integer sample width in bits, or null for encodings this player never outputs bit-perfect. */
    val bits: Int? get() = bitsOf(encoding)

    companion object {
        fun bitsOf(encoding: Int): Int? = when (encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> 16
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
            AudioFormat.ENCODING_PCM_32BIT -> 32
            else -> null
        }
    }
}

/**
 * What the decoder hands the sink. [minBits] is the narrowest integer width that still holds every
 * sample losslessly (float32 from the decoder carries at most 24 significant bits, so 24).
 */
data class SourcePcm(val sampleRate: Int, val channelCount: Int, val minBits: Int)

enum class PlanFailure { NO_MATCHING_SAMPLE_RATE, NO_MATCHING_CHANNELS, NO_WIDE_ENOUGH_ENCODING }

sealed interface PlanResult {
    data class Match(val format: OutputFormat) : PlanResult
    data class NoMatch(val failure: PlanFailure) : PlanResult
}

/**
 * Picks the DAC format to use for a source: the exact sample rate (a bit-perfect path never
 * resamples), the same channel count, and the narrowest integer encoding that doesn't lose bits.
 */
object BitPerfectPlanner {
    fun plan(source: SourcePcm, supported: List<OutputFormat>): PlanResult {
        val sameRate = supported.filter { it.sampleRate == source.sampleRate }
        if (sameRate.isEmpty()) return PlanResult.NoMatch(PlanFailure.NO_MATCHING_SAMPLE_RATE)
        val sameChannels = sameRate.filter { it.channelCount == source.channelCount }
        if (sameChannels.isEmpty()) return PlanResult.NoMatch(PlanFailure.NO_MATCHING_CHANNELS)
        val best = sameChannels
            .filter { (it.bits ?: 0) >= source.minBits }
            .minByOrNull { it.bits!! }
            ?: return PlanResult.NoMatch(PlanFailure.NO_WIDE_ENOUGH_ENCODING)
        return PlanResult.Match(best)
    }
}

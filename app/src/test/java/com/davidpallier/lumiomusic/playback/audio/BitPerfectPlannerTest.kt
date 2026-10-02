package com.davidpallier.lumiomusic.playback.audio

import android.media.AudioFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class BitPerfectPlannerTest {
    private val e16 = AudioFormat.ENCODING_PCM_16BIT
    private val e24 = AudioFormat.ENCODING_PCM_24BIT_PACKED
    private val e32 = AudioFormat.ENCODING_PCM_32BIT

    private val dac = listOf(
        OutputFormat(44100, 2, e16), OutputFormat(44100, 2, e24), OutputFormat(44100, 2, e32),
        OutputFormat(96000, 2, e24), OutputFormat(96000, 2, e32),
        OutputFormat(192000, 2, e32)
    )

    @Test fun `picks the narrowest encoding that holds the source`() {
        assertEquals(
            PlanResult.Match(OutputFormat(44100, 2, e16)),
            BitPerfectPlanner.plan(SourcePcm(44100, 2, 16), dac)
        )
        assertEquals(
            PlanResult.Match(OutputFormat(44100, 2, e24)),
            BitPerfectPlanner.plan(SourcePcm(44100, 2, 24), dac)
        )
        assertEquals(
            PlanResult.Match(OutputFormat(96000, 2, e24)),
            BitPerfectPlanner.plan(SourcePcm(96000, 2, 24), dac)
        )
    }

    @Test fun `32-bit sources need a 32-bit output`() {
        assertEquals(
            PlanResult.Match(OutputFormat(192000, 2, e32)),
            BitPerfectPlanner.plan(SourcePcm(192000, 2, 32), dac)
        )
        assertEquals(
            PlanResult.NoMatch(PlanFailure.NO_WIDE_ENOUGH_ENCODING),
            BitPerfectPlanner.plan(SourcePcm(96000, 2, 32).copy(minBits = 33), dac)
        )
    }

    @Test fun `never resamples - an unsupported rate is a failure`() {
        assertEquals(
            PlanResult.NoMatch(PlanFailure.NO_MATCHING_SAMPLE_RATE),
            BitPerfectPlanner.plan(SourcePcm(88200, 2, 24), dac)
        )
    }

    @Test fun `channel count must match`() {
        assertEquals(
            PlanResult.NoMatch(PlanFailure.NO_MATCHING_CHANNELS),
            BitPerfectPlanner.plan(SourcePcm(44100, 1, 16), dac)
        )
    }

    @Test fun `a 16-bit only dac cannot take a 24-bit source`() {
        val only16 = listOf(OutputFormat(44100, 2, e16))
        assertEquals(
            PlanResult.NoMatch(PlanFailure.NO_WIDE_ENOUGH_ENCODING),
            BitPerfectPlanner.plan(SourcePcm(44100, 2, 24), only16)
        )
    }
}

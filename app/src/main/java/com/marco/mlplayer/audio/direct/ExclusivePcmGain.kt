// SPDX-License-Identifier: GPL-3.0-or-later
// Da LastWave (https://github.com/Clash-Projects/LastWave-native, GPL-3.0), adattato per MLPlayer.
package com.marco.mlplayer.audio.direct

/** Software gain shared by all PCM formats entering the exclusive USB queue. */
internal object ExclusivePcmGain {
    fun apply(samples: FloatArray, gain: Float) {
        if (!gain.isFinite()) return
        val normalized = gain.coerceIn(0f, 1f)
        if (normalized >= 1f - 1e-6f) return
        for (index in samples.indices) {
            samples[index] = (samples[index] * normalized).coerceIn(-1f, 1f)
        }
    }
}

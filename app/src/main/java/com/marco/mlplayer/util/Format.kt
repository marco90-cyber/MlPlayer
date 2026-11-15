package com.marco.mlplayer.util

import java.util.Locale

/** Frequenza di campionamento leggibile: "44,1 kHz", "48 kHz". */
fun khz(hz: Int): String =
    if (hz % 1000 == 0) "${hz / 1000} kHz" else String.format(Locale.ITALY, "%.1f kHz", hz / 1000.0)

/** Durata o posizione in "m:ss". */
fun mmss(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.ITALY, "%d:%02d", s / 60, s % 60)
}

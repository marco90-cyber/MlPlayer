// SPDX-License-Identifier: GPL-3.0-or-later
// Pannello "Percorso del segnale" ripreso da LastWave (ui/player/SignalPathDialog.kt,
// https://github.com/Clash-Projects/LastWave-native, GPL-3.0), adattato ai dati di MLPlayer.
package com.marco.mlplayer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.marco.mlplayer.audio.direct.Route
import kotlinx.coroutines.delay
import java.util.Locale

// Colori del pannello (tema chiaro come in LastWave)
private val PSurface = Color(0xFFEDE7E8)
private val PSurfaceHighest = Color(0xFFE2DBDC)
private val POnSurface = Color(0xFF1E1A1B)
private val POnSurfaceVariant = Color(0xFF524345)
private val POutlineSolid = Color(0xFF857375)
private val POutlineVariant = Color(0xFFD7C1C3)
private val PPrimary = Color(0xFF9A4049)
private val POnPrimary = Color(0xFFFFFFFF)
private val PPrimaryContainer = Color(0xFFFFD9DB)
private val POnPrimaryContainer = Color(0xFF3F0010)
private val PTertiary = Color(0xFF7A5733)
private val POnTertiary = Color(0xFFFFFFFF)

private val TitleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp)
private val TitleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp)
private val BodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp)
private val BodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp)
private val LabelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
private val LabelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp)
private val DisplayMedium = TextStyle(fontSize = 45.sp, lineHeight = 52.sp)

private fun khz(hz: Int): String {
    val k = hz / 1000.0
    return if (k % 1.0 == 0.0) "${k.toInt()} kHz" else "$k kHz"
}

/**
 * Pannello dettagliato del percorso del segnale, aperto dalla riga della qualità (FLAC 24/96…).
 * Ogni riga è un controllo misurato; il verdetto BIT-PERFECT compare solo se passano tutti.
 */
@Composable
fun SignalPathDialog(env: AppEnv, onDismiss: () -> Unit) {
    val outs = env.engine.outputs
    val path = outs.path
    val r = path.report
    val t = env.engine.currentTrack
    var checked by remember { mutableStateOf("") }
    var glitches by remember { mutableIntStateOf(0) }
    LaunchedEffect(path) {
        while (true) {
            glitches = (outs.underrunsProvider?.invoke() ?: 0L).toInt()
            delay(1000)
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = true)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = PSurface,
            contentColor = POnSurface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Percorso del segnale",
                        style = TitleLarge, fontWeight = FontWeight.Bold, color = POnSurface,
                        modifier = Modifier.weight(1f),
                    )
                    VerdictPill(r.pill, r.pillLevel) {
                        env.feedback.tapScreen()
                        checked = outs.recheck()
                    }
                }
                Text(
                    if (r.valid) r.verdictText else path.verdict,
                    style = BodySmall,
                    color = when (r.pillLevel) {
                        2 -> PPrimary
                        1 -> PTertiary
                        else -> POnSurfaceVariant
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (checked.isNotEmpty()) {
                    Text(checked, style = BodySmall, color = PPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(8.dp))

                val srcLabel = t?.formatLabel ?: "—"
                // 1. Sorgente
                PipelineNode(
                    iconContent = { Text("♪", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PPrimary) },
                    title = "Source",
                    badge = if (r.pillLevel == 2) "LOSSLESS" else null,
                ) {
                    Text(srcLabel, style = BodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${khz(if (r.sourceRateHz > 0) r.sourceRateHz else 44100)}\n${t?.bits?.takeIf { it > 0 } ?: r.sourceBits} bit",
                        style = BodyMedium, fontWeight = FontWeight.SemiBold,
                    )
                }
                // 2. DSP
                PipelineNode(
                    iconContent = { Text("≈", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PPrimary) },
                    title = "DSP",
                    badge = if (!r.dspOn) "NO DSP" else null,
                ) {
                    Text(khz(r.dspRateHz), style = BodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(r.dspSummary, style = BodySmall, color = POnSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Label("Equalizer"); Value(r.eqLine)
                    Label("Tone"); Value(r.toneLine)
                    Label("Crossfade"); Value(r.crossfadeLine)
                    Label("Buffers"); Value(r.buffersLine)
                    Label("Latency"); Value("${r.dspLatencyMs} ms")
                }
                // 3. Volume
                PipelineNode(
                    iconContent = {
                        Box(
                            modifier = Modifier.size(16.dp).clip(CircleShape).border(1.5.dp, PPrimary, CircleShape),
                            contentAlignment = Alignment.TopStart,
                        ) {
                            Box(Modifier.padding(2.5.dp).size(3.5.dp).clip(CircleShape).background(PPrimary))
                        }
                    },
                    title = "Volume Control",
                ) {
                    Text(r.volumeMode, style = BodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(r.volumeDetail, style = BodySmall, color = POnSurface)
                    Label("Preamp")
                    Value(String.format(Locale.ROOT, "%.1fdB", r.preampDb))
                }
                // 4. Uscita
                PipelineNode(
                    iconContent = { Text("ψ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PPrimary) },
                    title = "Output",
                    badge = if (r.usbExclusive) "EXCLUSIVE" else null,
                ) {
                    Text(
                        r.engineName,
                        style = BodyMedium.copy(textDecoration = TextDecoration.Underline),
                        fontWeight = FontWeight.Bold, color = PPrimary,
                    )
                    Text(khz(r.outRateHz), style = BodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("${r.outBits} bit", style = BodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Label("Flags")
                    Text(
                        r.flags.joinToString(" "),
                        style = LabelSmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        color = POnSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Label("Latency"); Value("${r.outLatencyMs} ms")
                }
                // 5. Dispositivo
                PipelineNode(
                    iconContent = { Text("⎋", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PPrimary) },
                    title = "Output Device",
                    isLast = true,
                ) {
                    Text(
                        r.deviceName,
                        style = BodyMedium.copy(textDecoration = TextDecoration.Underline),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(khz(r.outRateHz), style = BodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("${r.outBits} bit", style = BodyMedium, fontWeight = FontWeight.SemiBold)
                }

                // Fedeltà e DSP
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = PSurfaceHighest.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, POutlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        ScoreColumn("Fidelity", "${r.fidelity}", if (r.pillLevel == 2) PPrimary else POnSurface)
                        ScoreColumn("DSP", r.dspScore, POnSurface)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Stato dello stream", style = LabelLarge, fontWeight = FontWeight.SemiBold, color = PPrimary)
                Spacer(Modifier.height(4.dp))
                val playing = env.engine.isPlaying
                val routeTrack = when (path.route) {
                    Route.UsbExclusive -> "Uscita USB diretta"
                    Route.DirectTrack, Route.AAudio -> "Uscita interna diretta"
                    Route.Android -> "AudioTrack condiviso"
                }
                HealthRow(
                    "Stream",
                    buildString {
                        append(if (playing) "In riproduzione" else "Inattivo")
                        if (r.dspRateHz > 0) append(" • ${r.dspRateHz} Hz")
                        append(" • $routeTrack")
                    },
                )
                HealthRow("Disturbi", if (outs.underrunsProvider != null) glitches.toString() else "—")

                Spacer(Modifier.height(8.dp))
                if (r.usbExclusive) {
                    Text("Hardware USB", style = LabelLarge, fontWeight = FontWeight.SemiBold, color = PPrimary)
                    Spacer(Modifier.height(4.dp))
                    HealthRow("Interfaccia", "usbdevfs isocrona (driver dell'app)")
                    HealthRow("Mixer di Android", "Escluso")
                    HealthRow("Endpoint esclusivo", r.endpoint)
                } else {
                    Text("Uscita interna", style = LabelLarge, fontWeight = FontWeight.SemiBold, color = PPrimary)
                    Spacer(Modifier.height(4.dp))
                    HealthRow("Percorso", r.routeName.ifBlank { "—" })
                    HealthRow("Uscita diretta", r.directStatus.ifBlank { "—" })
                    HealthRow("Endpoint esclusivo", r.endpoint)
                }

                if (env.settings.usbExclusive && outs.usbAudioDevice() != null && !outs.usbDacReady()) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { outs.ensureUsbPermission() },
                        colors = ButtonDefaults.buttonColors(containerColor = PPrimary, contentColor = POnPrimary),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Consenti l'accesso al DAC USB") }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        onDismiss()
                        env.tab = Tab.Settings
                        env.settingsNav.popToRoot()
                        env.settingsNav.push(SignalPathScreen)
                    }) { Text("Impostazioni", color = PPrimary) }
                    TextButton(onClick = onDismiss) { Text("Chiudi", color = PPrimary) }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = LabelSmall, color = POutlineSolid, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Value(text: String) {
    Text(text, style = BodySmall, color = POnSurface)
}

@Composable
private fun ScoreColumn(title: String, value: String, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = TitleMedium, fontWeight = FontWeight.Bold, color = POnSurface)
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier.size(16.dp).clip(CircleShape).background(POnSurfaceVariant.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("i", style = LabelSmall, fontWeight = FontWeight.Bold, color = POnSurfaceVariant, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(value, style = DisplayMedium, fontWeight = FontWeight.Black, color = color)
    }
}

@Composable
private fun VerdictPill(text: String, level: Int, onClick: () -> Unit) {
    val bg = when (level) {
        2 -> PPrimary
        1 -> PTertiary
        else -> PSurfaceHighest
    }
    val fg = when (level) {
        2 -> POnPrimary
        1 -> POnTertiary
        else -> POnSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, style = LabelSmall, fontWeight = FontWeight.ExtraBold, color = fg)
    }
}

@Composable
private fun HealthRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(
            label, style = BodySmall, color = POnSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
        )
        Text(value, style = BodySmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, color = POnSurface, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PipelineNode(
    iconContent: @Composable () -> Unit,
    title: String,
    badge: String? = null,
    isLast: Boolean = false,
    content: @Composable () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp).fillMaxHeight(),
        ) {
            Box(
                modifier = Modifier.size(26.dp).clip(CircleShape).background(PSurfaceHighest.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center,
            ) { iconContent() }
            if (!isLast) {
                Box(Modifier.weight(1f).width(1.5.dp).background(POutlineVariant.copy(alpha = 0.6f)))
                Text(
                    "↓", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = POutlineSolid.copy(alpha = 0.8f),
                    lineHeight = 11.sp, modifier = Modifier.padding(vertical = 1.dp),
                )
                Box(Modifier.weight(1f).width(1.5.dp).background(POutlineVariant.copy(alpha = 0.6f)))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(bottom = if (isLast) 6.dp else 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = TitleMedium.copy(textDecoration = TextDecoration.Underline),
                    fontWeight = FontWeight.Bold, color = POnSurface,
                )
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(PPrimaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(badge, style = LabelSmall, fontWeight = FontWeight.ExtraBold, color = POnPrimaryContainer, fontSize = 9.sp)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

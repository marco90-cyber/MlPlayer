package com.marco.mlplayer.ui

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.marco.mlplayer.audio.direct.Route
import com.marco.mlplayer.audio.direct.SignalPathInput
import com.marco.mlplayer.audio.direct.StreamHealthTracker
import com.marco.mlplayer.audio.direct.UsbDacInfo
import com.marco.mlplayer.audio.direct.evaluateSignalPath
import com.marco.mlplayer.audio.direct.mixerRateHz
import kotlinx.coroutines.delay

private val LOSSLESS = setOf("flac", "wav", "wave", "aif", "aiff", "alac", "ape", "wv", "dsf", "dff", "tta", "m4a")

/**
 * Apre il pannello "Percorso del segnale" di LastWave (codice originale in
 * [com.marco.mlplayer.ui.SignalPathDialog]) con i dati reali di MlPlayer.
 */
@Composable
fun SignalPathHost(env: AppEnv, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val engine = env.engine
    val outputs = engine.outputs
    val am = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val health = remember { StreamHealthTracker() }
    var tick by remember { mutableIntStateOf(0) }
    var drift by remember { mutableStateOf<Double?>(null) }

    // Campiona una volta al secondo, come LastWave.
    LaunchedEffect(Unit) {
        health.reset()
        while (true) {
            drift = runCatching {
                health.sample(engine.player.currentPosition, SystemClock.elapsedRealtime(), engine.player.isPlaying)
            }.getOrNull()
            tick++
            delay(1000)
        }
    }

    val lw = outputs.lw
    val path = outputs.path
    val track = engine.currentTrack
    @Suppress("UNUSED_EXPRESSION") tick

    val usbOut = outputs.androidUsbOutput()
    val usbDev = outputs.usbAudioDevice()
    val dac = if (usbOut != null || usbDev != null) UsbDacInfo(
        name = usbOut?.productName?.toString()?.takeIf { it.isNotBlank() } ?: usbDev?.productName ?: "USB DAC",
        vendorId = usbDev?.vendorId ?: -1,
        productId = usbDev?.productId ?: -1,
        sampleRatesHz = usbOut?.sampleRates?.toList() ?: emptyList(),
        channelCounts = usbOut?.channelCounts?.toList() ?: emptyList(),
        usbPermissionGranted = outputs.usbDacReady(),
        hasUsbPeripheral = usbDev != null,
        deviceId = usbOut?.id ?: -1,
    ) else null
    val routedToDac = dac != null && runCatching {
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }.getOrDefault(false)

    val info = engine.eqProcessor.info
    val dspOn = info.active && !engine.eqProcessor.forceBypass
    val usb = outputs.usb
    val usbActive = path.route == Route.UsbExclusive && usb.isActive()
    val volMax = runCatching { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(15)
    val vol = runCatching { am.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(volMax)

    val input = SignalPathInput(
        sourceLabel = track?.formatLabel ?: "—",
        sourceRateHz = (if (lw.valid) lw.sourceRateHz else track?.sampleRate)?.takeIf { it > 0 },
        sourceBitDepth = (if (lw.valid) lw.sourceBits else track?.bits)?.takeIf { it > 0 },
        isLossless = track?.ext?.lowercase() in LOSSLESS,
        appOutputRateHz = if (usbActive) usb.currentRateHz() else lw.appRateHz,
        platformMixerRateHz = am.mixerRateHz(),
        dspBypassEnabled = !dspOn,
        crossfadeMixing = false,
        speed = runCatching { engine.player.playbackParameters.speed }.getOrDefault(1f),
        appVolume = runCatching { engine.player.volume }.getOrDefault(1f),
        systemVolume = vol,
        systemVolumeMax = volMax,
        systemVolumeFixed = runCatching { am.isVolumeFixed }.getOrDefault(false),
        dac = dac,
        routedToDac = routedToDac,
        routeVerified = outputs.usbBitPerfect.isConfigured(),
        driftPpm = drift,
        glitchCount = health.glitchCount + (outputs.underrunsProvider?.invoke() ?: 0L),
        isPlaying = engine.isPlaying,
        platformBitPerfectConfigured = outputs.usbBitPerfect.isConfigured(),
        usbExclusiveActive = usbActive,
        exclusiveClockMatched = usb.isClockMatched(),
        exclusiveHardwareVolume = usb.usesHardwareVolume(),
        exclusiveFailureReason = usb.lastFailureReason,
        clockFallbackResampled = usb.isClockFallbackActive(),
        internalDirect = lw.session,
        internalDirectWanted = outputs.directWanted,
        internalDirectFailure = lw.failure,
        bypassRequested = outputs.bitPerfect,
        wireBits = usb.activeWireBits(),
        parametricEqActive = false,
    )
    val report = evaluateSignalPath(input)
    val needsPermission = usbDev != null && outputs.usbWanted && !outputs.usbDacReady()

    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicLightColorScheme(context)
    else lightColorScheme(
        primary = Color(0xFF9A4049), onPrimary = Color.White,
        primaryContainer = Color(0xFFFFD9DB), onPrimaryContainer = Color(0xFF3F0010),
        tertiary = Color(0xFF7A5733),
    )
    MaterialTheme(colorScheme = scheme) {
        SignalPathDialog(
            report = report,
            needsUsbPermission = needsPermission,
            onRequestUsbAccess = { outputs.ensureUsbPermission() },
            onDismiss = onDismiss,
            internalAvailability = lw.availability,
            internalEnabled = outputs.directWanted,
            internalUnderruns = if (lw.session != null) outputs.underrunsProvider?.invoke() else null,
            usbDiagnostic = { usb.diagnosticReport() },
        )
    }
}

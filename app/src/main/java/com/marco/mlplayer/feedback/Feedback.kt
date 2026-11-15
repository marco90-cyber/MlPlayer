package com.marco.mlplayer.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.marco.mlplayer.data.Settings
import com.marco.mlplayer.util.AppLog

/**
 * Feedback tattile dei tocchi. Solo vibrazione: i suoni dei click sono stati tolti
 * insieme alla grafica Hi-Fi (manopole e tasti in metallo).
 */
class Feedback(context: Context, private val settings: Settings) {

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    } catch (_: Throwable) {
        null
    }

    fun tapScreen() {
        if (settings.hapticScreen) tick()
    }

    /** Piccola vibrazione leggera (un passo di 1 dB degli slider). */
    fun softTick() {
        if (settings.hapticScreen) vibrate(predefined(0), VibrationEffect.createOneShot(6, 60))
    }

    /** "Scatto" di centraggio (zero dello slider/manopola): vibrazione più lunga e netta. */
    fun detent() {
        if (settings.hapticScreen) vibrate(null, VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** Usato dalla prova nelle impostazioni. */
    fun test() = tick(strong = true)

    private fun tick(strong: Boolean = false) {
        val level = if (strong) maxOf(settings.hapticStrength, 1) else settings.hapticStrength
        val fallback = when (level) {
            0 -> VibrationEffect.createOneShot(6, 60)
            1 -> VibrationEffect.createOneShot(10, 130)
            else -> VibrationEffect.createOneShot(16, 255)
        }
        vibrate(predefined(level), fallback)
    }

    private fun predefined(level: Int): VibrationEffect? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.createPredefined(
            when (level) {
                0 -> VibrationEffect.EFFECT_TICK
                1 -> VibrationEffect.EFFECT_CLICK
                else -> VibrationEffect.EFFECT_HEAVY_CLICK
            }
        ) else null

    private fun vibrate(effect: VibrationEffect?, fallback: VibrationEffect) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            v.vibrate(effect ?: fallback)
        } catch (e: Throwable) {
            AppLog.d("Feedback", "Vibrazione non riuscita: ${e.message}")
        }
    }
}

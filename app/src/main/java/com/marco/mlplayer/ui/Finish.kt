package com.marco.mlplayer.ui

import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily

/**
 * Finitura del lettore: tutti i colori del frontale, degli strumenti, del display e
 * delle schermate interne (equalizzatore, analizzatore, uscita, log).
 */
class Finish(
    val id: String,
    val name: String,
    /** Sfondo dietro il frontale (per "noce" sono le venature del legno). */
    val bg: List<Color>,
    val wood: Boolean = false,
    val plateTop: Color,
    val plateBottom: Color,
    val plateBorder: Color,
    /** Righe della spazzolatura (chiare e scure). */
    val brushLight: Color,
    val brushDark: Color,
    /** Bordo a rilievo stile anni '90 al posto della spazzolatura. */
    val bevel: Boolean = false,
    val engrave: Color,
    val engraveDim: Color,
    val meterFace: Color,
    val meterGlow: Color,
    val scale: Color,
    val scaleRed: Color,
    val needle: Color,
    val meterText: Color,
    val vfd: Color,
    val vfdDim: Color,
    val vfdBg: Color,
    val knobLight: Color,
    val knobDark: Color,
    val capLight: Color,
    val capDark: Color,
    val knobMark: Color,
    val btnTop: Color,
    val btnBottom: Color,
    val btnBorder: Color,
    val btnIcon: Color,
    val led: Color,
    val led2: Color,
    // schermate interne (sempre scure, come il vetro di un display)
    val scrBg: Color,
    val scrPanel: Color,
    val scrChip: Color,
    val scrLine: Color,
    val scrDim: Color,
    /** Secondo colore (bassi nell'equalizzatore). */
    val accent2: Color,
    /** Spettro: colori bassa / media / alta intensità. */
    val specLow: Color,
    val specMid: Color,
    val specHigh: Color,
) {
    val lightBg: Boolean get() = bg.first().luminance() > 0.5f
}

object Finishes {
    val nero = Finish(
        id = "nero", name = "Nero e blu",
        bg = listOf(Color(0xFF0E0F11), Color(0xFF0E0F11)),
        plateTop = Color(0xFF24262B), plateBottom = Color(0xFF17181C), plateBorder = Color(0xFF33363C),
        brushLight = Color(0x0AFFFFFF), brushDark = Color(0x12000000),
        engrave = Color(0xFFC4C8D0), engraveDim = Color(0xFF8F949E),
        meterFace = Color(0xFF06121F), meterGlow = Color(0x592890FF),
        scale = Color(0xFFCFE6FF), scaleRed = Color(0xFFFF6A4D), needle = Color(0xFFF2F6FF), meterText = Color(0xFF9CC8FF),
        vfd = Color(0xFF7FDCFF), vfdDim = Color(0xFF2F6A80), vfdBg = Color(0xFF04080B),
        knobLight = Color(0xFFE4E6EA), knobDark = Color(0xFF8A8E96), capLight = Color(0xFF2C2E33), capDark = Color(0xFF141518),
        knobMark = Color(0xFF7FDCFF),
        btnTop = Color(0xFF2C2E33), btnBottom = Color(0xFF1A1B1F), btnBorder = Color(0xFF3D4047), btnIcon = Color(0xFFD5D8DE),
        led = Color(0xFFFFB020), led2 = Color(0xFF5AB8FF),
        scrBg = Color(0xFF07090C), scrPanel = Color(0xFF12161C), scrChip = Color(0xFF1E242C), scrLine = Color(0xFF34404C),
        scrDim = Color(0xFF93A4B4), accent2 = Color(0xFF5AB8FF),
        specLow = Color(0xFF7FDCFF), specMid = Color(0xFFFFB020), specHigh = Color(0xFFFF5A4A),
    )

    val champagne = Finish(
        id = "champagne", name = "Champagne",
        bg = listOf(Color(0xFF1A1712), Color(0xFF1A1712)),
        plateTop = Color(0xFFE3D3AD), plateBottom = Color(0xFFC9B486), plateBorder = Color(0xFFA8936A),
        brushLight = Color(0x14FFFFFF), brushDark = Color(0x0F785A28),
        engrave = Color(0xFF3A2F1C), engraveDim = Color(0xFF5C4C30),
        meterFace = Color(0xFFF3D58A), meterGlow = Color(0x8CFFBE3C),
        scale = Color(0xFF2A1F0E), scaleRed = Color(0xFFB3261E), needle = Color(0xFF1B140A), meterText = Color(0xFF4A3818),
        vfd = Color(0xFFFFB24A), vfdDim = Color(0xFF6A4512), vfdBg = Color(0xFF0D0904),
        knobLight = Color(0xFFF5E9C9), knobDark = Color(0xFFA68C58), capLight = Color(0xFFE9DCB9), capDark = Color(0xFFBBA477),
        knobMark = Color(0xFF3A2F1C),
        btnTop = Color(0xFFEADCB8), btnBottom = Color(0xFFC7B182), btnBorder = Color(0xFFA8936A), btnIcon = Color(0xFF3A2F1C),
        led = Color(0xFFFF8A1E), led2 = Color(0xFFFF8A1E),
        scrBg = Color(0xFF0D0A06), scrPanel = Color(0xFF1A140C), scrChip = Color(0xFF2A2014), scrLine = Color(0xFF4A3A24),
        scrDim = Color(0xFFC0A27A), accent2 = Color(0xFFFFD27A),
        specLow = Color(0xFFFFB24A), specMid = Color(0xFFFFD27A), specHigh = Color(0xFFFF5A3A),
    )

    val argento = Finish(
        id = "argento", name = "Argento",
        bg = listOf(Color(0xFFD7D9DC), Color(0xFFC9CCD0)),
        plateTop = Color(0xFFE9EAEC), plateBottom = Color(0xFFC3C6CB), plateBorder = Color(0xFFA3A7AD),
        brushLight = Color(0x2EFFFFFF), brushDark = Color(0x09000000),
        engrave = Color(0xFF26292E), engraveDim = Color(0xFF4A4E55),
        meterFace = Color(0xFF050708), meterGlow = Color(0x22FFFFFF),
        scale = Color(0xFF9FFFD2), scaleRed = Color(0xFFFF8A3D), needle = Color(0xFFFFFFFF), meterText = Color(0xFF7FE9BD),
        vfd = Color(0xFF7DFFC4), vfdDim = Color(0xFF1F5A42), vfdBg = Color(0xFF030605),
        knobLight = Color(0xFFFAFBFC), knobDark = Color(0xFF9CA0A7), capLight = Color(0xFFF4F5F7), capDark = Color(0xFFC2C5CA),
        knobMark = Color(0xFFD0401B),
        btnTop = Color(0xFFF1F2F4), btnBottom = Color(0xFFC9CCD1), btnBorder = Color(0xFF9A9EA5), btnIcon = Color(0xFF26292E),
        led = Color(0xFFFF7A1A), led2 = Color(0xFF2F9BFF),
        scrBg = Color(0xFF040706), scrPanel = Color(0xFF0E1512), scrChip = Color(0xFF18231E), scrLine = Color(0xFF2E4238),
        scrDim = Color(0xFF8FBFA8), accent2 = Color(0xFF6FC3FF),
        specLow = Color(0xFF7DFFC4), specMid = Color(0xFFFFC94A), specHigh = Color(0xFFFF6A3D),
    )

    val noce = Finish(
        id = "noce", name = "Noce",
        bg = listOf(Color(0xFF4A2C17), Color(0xFF5B3720), Color(0xFF3F2413), Color(0xFF6A4227), Color(0xFF4A2C17)),
        wood = true,
        plateTop = Color(0xFF1C1C1C), plateBottom = Color(0xFF0F0F0F), plateBorder = Color(0xFF2B2B2B),
        brushLight = Color(0x06FFFFFF), brushDark = Color(0x0D000000),
        engrave = Color(0xFFD9D2C3), engraveDim = Color(0xFFA39B8B),
        meterFace = Color(0xFF0A2A24), meterGlow = Color(0x593CE6AA),
        scale = Color(0xFFC8FFE9), scaleRed = Color(0xFFFF7A52), needle = Color(0xFFFFF6E0), meterText = Color(0xFF8FF0CC),
        vfd = Color(0xFF62F2C8), vfdDim = Color(0xFF1D5546), vfdBg = Color(0xFF030807),
        knobLight = Color(0xFFE4E6EA), knobDark = Color(0xFF8A8E96), capLight = Color(0xFF2C2E33), capDark = Color(0xFF141518),
        knobMark = Color(0xFF62F2C8),
        btnTop = Color(0xFF2A2A2A), btnBottom = Color(0xFF161616), btnBorder = Color(0xFF3A3A3A), btnIcon = Color(0xFFD9D2C3),
        led = Color(0xFFFFB020), led2 = Color(0xFF62F2C8),
        scrBg = Color(0xFF040807), scrPanel = Color(0xFF0D1714), scrChip = Color(0xFF16241F), scrLine = Color(0xFF2A4038),
        scrDim = Color(0xFF8CC2AE), accent2 = Color(0xFFFFB020),
        specLow = Color(0xFF62F2C8), specMid = Color(0xFFFFB020), specHigh = Color(0xFFFF6A4A),
    )

    val bianco = Finish(
        id = "bianco", name = "Bianco",
        bg = listOf(Color(0xFFECEAE6), Color(0xFFE2E0DB)),
        plateTop = Color(0xFFFBFAF8), plateBottom = Color(0xFFE9E7E3), plateBorder = Color(0xFFCFCCC6),
        brushLight = Color(0x00FFFFFF), brushDark = Color(0x00000000),
        engrave = Color(0xFF1F2023), engraveDim = Color(0xFF55575C),
        meterFace = Color(0xFFFDFCF9), meterGlow = Color(0x1F000000),
        scale = Color(0xFF1F2023), scaleRed = Color(0xFFC2410C), needle = Color(0xFFC2410C), meterText = Color(0xFF3A3C40),
        vfd = Color(0xFFFF9A3C), vfdDim = Color(0xFF5A3410), vfdBg = Color(0xFF121212),
        knobLight = Color(0xFFFFFFFF), knobDark = Color(0xFFCFCDC8), capLight = Color(0xFFFFFFFF), capDark = Color(0xFFDEDCD8),
        knobMark = Color(0xFFC2410C),
        btnTop = Color(0xFFFFFFFF), btnBottom = Color(0xFFE4E2DE), btnBorder = Color(0xFFC9C6C0), btnIcon = Color(0xFF1F2023),
        led = Color(0xFFEA580C), led2 = Color(0xFF2563EB),
        scrBg = Color(0xFF111111), scrPanel = Color(0xFF1B1B1B), scrChip = Color(0xFF282828), scrLine = Color(0xFF3E3E3E),
        scrDim = Color(0xFFB4ACA4), accent2 = Color(0xFF7AA7FF),
        specLow = Color(0xFFFF9A3C), specMid = Color(0xFFFFC46B), specHigh = Color(0xFFFF4A2A),
    )

    /** Ispirata ai lettori per PC di fine anni '90: frontale a rilievo, display nero con testo verde. */
    val retro = Finish(
        id = "retro", name = "Retrò 97",
        bg = listOf(Color(0xFF101018), Color(0xFF101018)),
        plateTop = Color(0xFF3B3C52), plateBottom = Color(0xFF282939), plateBorder = Color(0xFF14141E),
        brushLight = Color(0x00FFFFFF), brushDark = Color(0x00000000),
        bevel = true,
        engrave = Color(0xFFE2E4EE), engraveDim = Color(0xFFA9ACC2),
        meterFace = Color(0xFF000000), meterGlow = Color(0x2200FF40),
        scale = Color(0xFF28E040), scaleRed = Color(0xFFFF4030), needle = Color(0xFFE8FFE0), meterText = Color(0xFF28E040),
        vfd = Color(0xFF00F040), vfdDim = Color(0xFF0E4A18), vfdBg = Color(0xFF000000),
        knobLight = Color(0xFFBDBFD0), knobDark = Color(0xFF5E6078), capLight = Color(0xFF4A4C66), capDark = Color(0xFF2A2B3B),
        knobMark = Color(0xFFFFD020),
        btnTop = Color(0xFF52546E), btnBottom = Color(0xFF34354A), btnBorder = Color(0xFF14141E), btnIcon = Color(0xFFE2E4EE),
        led = Color(0xFFFFD020), led2 = Color(0xFF00F040),
        scrBg = Color(0xFF000000), scrPanel = Color(0xFF0C0D14), scrChip = Color(0xFF1C1D2A), scrLine = Color(0xFF34364A),
        scrDim = Color(0xFF9EC8A4), accent2 = Color(0xFFFFD020),
        specLow = Color(0xFF00E040), specMid = Color(0xFFE8E020), specHigh = Color(0xFFFF3020),
    )

    val all = listOf(nero, champagne, argento, noce, bianco, retro)

    fun byId(id: String): Finish = all.firstOrNull { it.id == id } ?: nero
}

/** Finitura e opzioni di aspetto attive (osservabili da Compose). */
object Hifi {
    var finish by mutableStateOf(Finishes.nero)
    var glow by mutableStateOf(true)
    var brushed by mutableStateOf(true)
    /** Intensità della luce degli strumenti (0,4 .. 1). */
    var light by mutableFloatStateOf(1f)

    val f: Finish get() = finish

    val Condensed: FontFamily = FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
    val CondensedBold: FontFamily = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))
    val Mono: FontFamily = FontFamily.Monospace
}

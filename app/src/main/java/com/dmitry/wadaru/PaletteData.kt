package com.dmitry.wadaru

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class WadaColor(
    val wadaId: String?,
    val name: String,
    val slug: String,
    val hex: String,
    val rgb: List<Int>,
    val cmyk: List<Int>?,
    val lab: List<Double>?
)

data class WadaPalette(
    val id: String,
    val volume: Int,
    val number: Int,
    val colors: List<WadaColor>
) {
    val displayNumber: String get() = if (volume == 1) "I · %03d".format(number) else "II · %03d".format(number)
}

enum class Temperature(val label: String) {
    WARM("Тёплые"), COOL("Холодные"), BALANCED("Смешанные")
}

enum class LightnessBand(val label: String) {
    LIGHT("Светлые"), MEDIUM("Средние"), DARK("Тёмные")
}

enum class ChromaBand(val label: String) {
    MUTED("Приглушённые"), MEDIUM("Умеренные"), VIVID("Насыщенные")
}

enum class ContrastBand(val label: String) {
    LOW("Низкий контраст"), MEDIUM("Средний контраст"), HIGH("Высокий контраст")
}

data class PaletteAnalysis(
    val temperature: Temperature,
    val lightness: LightnessBand,
    val chroma: ChromaBand,
    val contrast: ContrastBand,
    val dominantFamily: String,
    val short: String,
    val why: String,
    val usage: String
)

object PaletteRepository {
    fun load(context: Context): List<WadaPalette> {
        val raw = context.assets.open("palettes.json").bufferedReader().use { it.readText() }
        val root = JSONObject(raw)
        val arr = root.getJSONArray("palettes")
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                add(
                    WadaPalette(
                        id = p.getString("id"),
                        volume = p.getInt("volume"),
                        number = p.getInt("number"),
                        colors = p.getJSONArray("colors").toColors()
                    )
                )
            }
        }
    }

    private fun JSONArray.toColors(): List<WadaColor> = buildList(length()) {
        for (i in 0 until length()) {
            val c = getJSONObject(i)
            add(
                WadaColor(
                    wadaId = c.optString("wadaId").takeIf { it.isNotBlank() && it != "null" },
                    name = c.getString("name"),
                    slug = c.getString("slug"),
                    hex = c.getString("hex"),
                    rgb = c.optJSONArray("rgb").toInts() ?: rgbFromHex(c.getString("hex")),
                    cmyk = c.optJSONArray("cmyk").toInts(),
                    lab = c.optJSONArray("lab").toDoubles()
                )
            )
        }
    }

    private fun JSONArray?.toInts(): List<Int>? {
        if (this == null) return null
        return List(length()) { getInt(it) }
    }

    private fun JSONArray?.toDoubles(): List<Double>? {
        if (this == null) return null
        return List(length()) { getDouble(it) }
    }

    private fun rgbFromHex(hex: String): List<Int> {
        val c = Color.parseColor(hex)
        return listOf(Color.red(c), Color.green(c), Color.blue(c))
    }
}

object PaletteAnalyzer {
    private data class Hsl(val h: Double, val s: Double, val l: Double)

    fun analyze(palette: WadaPalette): PaletteAnalysis {
        val hsl = palette.colors.map { rgbToHsl(it.rgb) }
        val temperatures = hsl.map { isWarm(it.h) }
        val warmCount = temperatures.count { it }
        val coolCount = temperatures.size - warmCount
        val temperature = when {
            warmCount >= coolCount + 2 -> Temperature.WARM
            coolCount >= warmCount + 2 -> Temperature.COOL
            else -> Temperature.BALANCED
        }

        val avgL = hsl.map { it.l }.average()
        val lightness = when {
            avgL >= .68 -> LightnessBand.LIGHT
            avgL <= .38 -> LightnessBand.DARK
            else -> LightnessBand.MEDIUM
        }

        val avgS = hsl.map { it.s }.average()
        val chroma = when {
            avgS >= .58 -> ChromaBand.VIVID
            avgS <= .30 -> ChromaBand.MUTED
            else -> ChromaBand.MEDIUM
        }

        val luminances = palette.colors.map { relativeLuminance(it.rgb) }
        val spread = (luminances.maxOrNull() ?: 0.0) - (luminances.minOrNull() ?: 0.0)
        val contrast = when {
            spread >= .58 -> ContrastBand.HIGH
            spread <= .25 -> ContrastBand.LOW
            else -> ContrastBand.MEDIUM
        }

        val families = hsl.map { hueFamily(it) }
        val dominant = families.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "Смешанная"
        val neutrals = hsl.count { it.s < .16 }
        val deepestIndex = luminances.indices.minByOrNull { luminances[it] } ?: 0
        val lightestIndex = luminances.indices.maxByOrNull { luminances[it] } ?: 0
        val deepest = RussianColorNames.of(palette.colors[deepestIndex].name)
        val lightest = RussianColorNames.of(palette.colors[lightestIndex].name)

        val short = buildString {
            append(
                when (temperature) {
                    Temperature.WARM -> "Палитра в основном тёплая"
                    Temperature.COOL -> "Палитра в основном холодная"
                    Temperature.BALANCED -> "Палитра балансирует тёплые и холодные оттенки"
                }
            )
            append(", ")
            append(
                when (contrast) {
                    ContrastBand.HIGH -> "с заметным светлотным контрастом."
                    ContrastBand.MEDIUM -> "со средним светлотным контрастом."
                    ContrastBand.LOW -> "с мягким светлотным контрастом."
                }
            )
        }

        val why = buildString {
            if (temperature == Temperature.BALANCED) {
                append("Гармония строится на температурном диалоге: тёплые и холодные участки уравновешивают друг друга. ")
            } else {
                append("Основная группа оттенков держится в одной температуре, поэтому сочетание воспринимается цельно. ")
            }
            when (contrast) {
                ContrastBand.HIGH -> append("Большой разрыв по светлоте создаёт ясную иерархию и делает акценты читаемыми. ")
                ContrastBand.MEDIUM -> append("Разница по светлоте достаточна для разделения ролей, но не разрушает единство. ")
                ContrastBand.LOW -> append("Близкая светлота связывает цвета в спокойную, почти тональную композицию. ")
            }
            if (neutrals > 0) {
                append("Нейтральные оттенки работают как буфер между более активными цветами. ")
            }
            append("Самый светлый тон — $lightest; самый глубокий — $deepest.")
        }

        val usage = when {
            palette.colors.size >= 6 ->
                "Лучше использовать не все цвета в равной доле: выберите 1–2 основных, 2–3 поддерживающих и оставьте остальные для небольших акцентов. Подходит для сложной одежды, интерьера, айдентики и иллюстрации."
            contrast == ContrastBand.HIGH ->
                "Подходит для одежды, плакатов, интерфейсов и упаковки. Глубокий цвет удобно взять за основу, светлый — за фон, а самый насыщенный — за акцент."
            chroma == ChromaBand.MUTED ->
                "Хорошо работает в одежде, интерьере и редакционном дизайне, где нужна спокойная сложная гамма. Сохраняйте близкие по площади базовые оттенки и один небольшой акцент."
            else ->
                "Универсальная схема для одежды, графики, интерьера и оформления. Один цвет лучше назначить базовым, второй — поддерживающим, остальные использовать дозированно."
        }

        return PaletteAnalysis(
            temperature = temperature,
            lightness = lightness,
            chroma = chroma,
            contrast = contrast,
            dominantFamily = dominant,
            short = short,
            why = why,
            usage = usage
        )
    }

    fun searchBlob(palette: WadaPalette, analysis: PaletteAnalysis): String =
        buildString {
            append(palette.id).append(' ')
            append(palette.number).append(' ')
            append(if (palette.volume == 1) "том 1 первый" else "том 2 второй").append(' ')
            palette.colors.forEach { append(it.name).append(' ').append(RussianColorNames.of(it.name)).append(' ').append(it.hex).append(' ') }
            append(analysis.temperature.label).append(' ')
            append(analysis.lightness.label).append(' ')
            append(analysis.chroma.label).append(' ')
            append(analysis.contrast.label).append(' ')
            append(analysis.dominantFamily)
        }.lowercase()

    private fun hueFamily(hsl: Hsl): String {
        if (hsl.s < .14) return "Нейтральные"
        val h = hsl.h
        return when {
            h < 15 || h >= 345 -> "Красные"
            h < 45 -> "Оранжевые"
            h < 72 -> "Жёлтые"
            h < 165 -> "Зелёные"
            h < 195 -> "Бирюзовые"
            h < 255 -> "Синие"
            h < 300 -> "Фиолетовые"
            else -> "Розовые"
        }
    }

    private fun isWarm(h: Double): Boolean =
        h < 85.0 || h >= 300.0

    private fun rgbToHsl(rgb: List<Int>): Hsl {
        val r = rgb[0] / 255.0
        val g = rgb[1] / 255.0
        val b = rgb[2] / 255.0
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = mx - mn
        val l = (mx + mn) / 2.0
        val s = if (d == 0.0) 0.0 else d / (1.0 - abs(2.0 * l - 1.0))
        val h = when {
            d == 0.0 -> 0.0
            mx == r -> 60.0 * (((g - b) / d) % 6.0)
            mx == g -> 60.0 * (((b - r) / d) + 2.0)
            else -> 60.0 * (((r - g) / d) + 4.0)
        }.let { if (it < 0) it + 360.0 else it }
        return Hsl(h, s, l)
    }

    private fun relativeLuminance(rgb: List<Int>): Double {
        fun channel(v: Int): Double {
            val c = v / 255.0
            return if (c <= .04045) c / 12.92 else Math.pow((c + .055) / 1.055, 2.4)
        }
        return .2126 * channel(rgb[0]) + .7152 * channel(rgb[1]) + .0722 * channel(rgb[2])
    }
}

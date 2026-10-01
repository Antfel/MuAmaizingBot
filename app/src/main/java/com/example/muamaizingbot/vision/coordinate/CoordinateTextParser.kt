package com.example.muamaizingbot.vision.coordinate

import com.example.muamaizingbot.maps.CoordinateBounds

object CoordinateTextParser {

    fun parseCoordinates(rawText: String?): Pair<Int, Int>? {
        val text = normalizeCoordOcr(rawText.orEmpty())

        val parenMatch = Regex("""\(\s*(\d+)\s*,\s*(\d+)\s*\)""").find(text)
        if (parenMatch != null) {
            return parenMatch.groupValues[1].toInt() to parenMatch.groupValues[2].toInt()
        }

        val commaMatch = Regex("""(\d+)\s*,\s*(\d+)""").find(text)
        if (commaMatch != null) {
            return commaMatch.groupValues[1].toInt() to commaMatch.groupValues[2].toInt()
        }

        val numbers = Regex("""\d+""").findAll(text).map { it.value.toInt() }.toList()
        if (numbers.size >= 2) {
            return numbers[0] to numbers[1]
        }

        return null
    }

    /**
     * HUD OCR often turns leading `1` into `n`/`N`/`l`/`I`/`|`
     * (e.g. `n52,95)` → `(152,95)`, `n28,122)` → `(128,122)`).
     */
    fun normalizeCoordOcr(raw: String): String {
        var s = raw.trim()
            .replace('，', ',')
            .replace('．', '.')
            .replace('{', '(')
            .replace('}', ')')
            .replace('[', '(')
            .replace(']', ')')
        // Letter/pipe that stands in for digit 1 immediately before a digit run.
        s = s.replace(Regex("""(?<![0-9])[nNilI|](?=\d)"""), "1")
        // Same for O/o → 0 when glued to digits (less common on this HUD).
        s = s.replace(Regex("""(?<=\d)[Oo](?=\d|,|\))"""), "0")
        s = s.replace(Regex("""(?<![0-9])[Oo](?=\d)"""), "0")
        return s
    }

    fun applyCoordinateBounds(coords: Pair<Int, Int>, bounds: CoordinateBounds?): Pair<Int, Int>? {
        if (bounds == null) {
            return coords
        }

        var (x, y) = coords
        x = correctAxisValue(x, bounds.xMin, bounds.xMax)
        y = correctAxisValue(y, bounds.yMin, bounds.yMax)

        if (x !in bounds.xMin..bounds.xMax) {
            return null
        }
        if (y !in bounds.yMin..bounds.yMax) {
            return null
        }

        return x to y
    }

    private fun correctAxisValue(value: Int, minValue: Int, maxValue: Int): Int {
        if (value in minValue..maxValue) {
            return value
        }

        val valueStr = value.toString()
        if (!valueStr.startsWith("1") || valueStr.length <= 1) {
            return value
        }

        val corrected = valueStr.substring(1).toIntOrNull() ?: return value
        return if (corrected in minValue..maxValue) corrected else value
    }

    /**
     * HUD OCR often drops digits from a known good axis (e.g. 161 → 61 / 16 / 1).
     * Only when [read] is a proper shorter **prefix or suffix** of [expected], not equal.
     * Mid-string fragments are rejected (e.g. 4 inside 247, 6 inside 161) — that caused
     * false boss arrivals mid-walk. Also allows one-digit OCR noise on a same-length
     * contiguous prefix/suffix fragment (e.g. 114 → 13 near fragment "14").
     */
    fun looksLikeTruncatedAxis(read: Int, expected: Int): Boolean {
        if (read == expected) return false
        val r = read.toString()
        val e = expected.toString()
        if (r.length >= e.length) return false
        if (e.startsWith(r) || e.endsWith(r)) return true
        return isNearDigitFragment(r, e)
    }

    /** [r] is within ±1 of the leading- or trailing-truncated form of [e]. */
    private fun isNearDigitFragment(r: String, e: String): Boolean {
        if (r.length < 2) return false
        val drop = e.length - r.length
        if (drop < 1) return false
        val readVal = r.toIntOrNull() ?: return false
        val suffixVal = e.substring(drop).toIntOrNull() ?: return false
        if (kotlin.math.abs(suffixVal - readVal) <= 1) return true
        val prefixVal = e.substring(0, r.length).toIntOrNull() ?: return false
        return kotlin.math.abs(prefixVal - readVal) <= 1
    }

    /**
     * True when at least one axis looks truncated and both axes remain plausible
     * versus the farm-spot target. This avoids treating a real move such as
     * `(1,190)` as sticky only because `1` is a loose fragment of target `161`.
     */
    fun looksLikeTruncatedHudRead(
        current: Pair<Int, Int>,
        target: Pair<Int, Int>,
        tolerance: Int = 0,
    ): Boolean {
        val xTruncated = looksLikeTruncatedAxis(current.first, target.first)
        val yTruncated = looksLikeTruncatedAxis(current.second, target.second)
        val xPlausible = xTruncated || kotlin.math.abs(current.first - target.first) <= tolerance
        val yPlausible = yTruncated || kotlin.math.abs(current.second - target.second) <= tolerance
        return (xTruncated || yTruncated) && xPlausible && yPlausible
    }
}

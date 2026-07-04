package com.example.muamaizingbot.vision.coordinate

import com.example.muamaizingbot.maps.CoordinateBounds

object CoordinateTextParser {

    fun parseCoordinates(rawText: String?): Pair<Int, Int>? {
        val text = rawText.orEmpty()

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
}

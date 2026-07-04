package com.example.muamaizingbot.maps

import kotlin.math.roundToInt

object CoordinateMapping {

    fun hasMapping(mapDef: MapDefinition?): Boolean {
        val mapping = mapDef?.coordinateMapping ?: return false
        return mapping.coordX.size == 3 && mapping.coordY.size == 3
    }

    fun pixelToMapCoord(mapDef: MapDefinition, pixelX: Int, pixelY: Int): Pair<Int, Int>? {
        val mapping = mapDef.coordinateMapping ?: return null
        val maintenance = mapDef.maintenance

        val sourceW = mapping.sourceWidth.toDouble()
        val sourceH = mapping.sourceHeight.toDouble()
        val maintW = (maintenance?.imageWidth ?: mapping.sourceWidth).toDouble()
        val maintH = (maintenance?.imageHeight ?: mapping.sourceHeight).toDouble()

        val (px, py) = if (maintW > 0 && maintH > 0) {
            pixelX * (sourceW / maintW) to pixelY * (sourceH / maintH)
        } else {
            pixelX.toDouble() to pixelY.toDouble()
        }

        val a = mapping.coordX[0]
        val b = mapping.coordX[1]
        val c = mapping.coordX[2]
        val d = mapping.coordY[0]
        val e = mapping.coordY[1]
        val f = mapping.coordY[2]

        val coordX = (a * px + b * py + c).roundToInt()
        val coordY = (d * px + e * py + f).roundToInt()
        return coordX to coordY
    }
}

package com.example.muamaizingbot.maps

import com.example.muamaizingbot.vision.coord.RefCoords
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Affine map between maintenance-reference pixels and in-game HUD coordinates.
 *
 * SpotPicker / farm spots author pixels in logical [RefCoords.REF_WIDTH]×[RefCoords.REF_HEIGHT].
 * The transform coefficients are defined in [AffineTransform] `sourceWidth`×`sourceHeight`
 * (historically 2560×1440). Maintenance PNG size is declared separately and is used only
 * when converting native PNG taps; prefer calling the REF overloads from the picker.
 */
object CoordinateMapping {

    private const val DET_EPS = 1e-12

    fun hasMapping(mapDef: MapDefinition?): Boolean {
        val mapping = mapDef?.coordinateMapping ?: return false
        return mapping.coordX.size == 3 && mapping.coordY.size == 3
    }

    /**
     * @param refX pixel X in REF space (2560×1440 logical), as stored on [com.example.muamaizingbot.profile.FarmLocation]
     * @param refY pixel Y in REF space
     */
    fun pixelToMapCoord(mapDef: MapDefinition, refX: Int, refY: Int): Pair<Int, Int>? {
        val mapping = mapDef.coordinateMapping ?: return null
        val (px, py) = refToSource(refX, refY, mapping)
        return applyForward(mapping, px, py)
    }

    /**
     * Inverse of [pixelToMapCoord]: game coords → REF pixels for the SpotPicker marker.
     */
    fun mapCoordToPixel(mapDef: MapDefinition, gameX: Int, gameY: Int): Pair<Int, Int>? {
        val mapping = mapDef.coordinateMapping ?: return null
        val source = invertToSource(mapping, gameX.toDouble(), gameY.toDouble()) ?: return null
        return sourceToRef(source.first, source.second, mapping)
    }

    /** Maintenance-native PNG pixels → game coords (when not using REF). */
    fun maintPixelToMapCoord(mapDef: MapDefinition, maintX: Int, maintY: Int): Pair<Int, Int>? {
        val mapping = mapDef.coordinateMapping ?: return null
        val maintenance = mapDef.maintenance
        val sourceW = mapping.sourceWidth.toDouble()
        val sourceH = mapping.sourceHeight.toDouble()
        val maintW = (maintenance?.imageWidth ?: mapping.sourceWidth).toDouble()
        val maintH = (maintenance?.imageHeight ?: mapping.sourceHeight).toDouble()
        if (maintW <= 0 || maintH <= 0) {
            return applyForward(mapping, maintX.toDouble(), maintY.toDouble())
        }
        val px = maintX * (sourceW / maintW)
        val py = maintY * (sourceH / maintH)
        return applyForward(mapping, px, py)
    }

    private fun refToSource(refX: Int, refY: Int, mapping: AffineTransform): Pair<Double, Double> {
        val px = refX.toDouble() * mapping.sourceWidth / RefCoords.REF_WIDTH
        val py = refY.toDouble() * mapping.sourceHeight / RefCoords.REF_HEIGHT
        return px to py
    }

    private fun sourceToRef(px: Double, py: Double, mapping: AffineTransform): Pair<Int, Int> {
        val refX = (px * RefCoords.REF_WIDTH / mapping.sourceWidth).roundToInt()
        val refY = (py * RefCoords.REF_HEIGHT / mapping.sourceHeight).roundToInt()
        return refX to refY
    }

    private fun applyForward(mapping: AffineTransform, px: Double, py: Double): Pair<Int, Int> {
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

    /**
     * Solve [a b; d e] * [px; py] = [gx-c; gy-f] for source-space pixels.
     */
    private fun invertToSource(
        mapping: AffineTransform,
        gameX: Double,
        gameY: Double,
    ): Pair<Double, Double>? {
        val a = mapping.coordX[0]
        val b = mapping.coordX[1]
        val c = mapping.coordX[2]
        val d = mapping.coordY[0]
        val e = mapping.coordY[1]
        val f = mapping.coordY[2]
        val det = a * e - b * d
        if (abs(det) < DET_EPS) {
            return null
        }
        val rx = gameX - c
        val ry = gameY - f
        val px = (e * rx - b * ry) / det
        val py = (-d * rx + a * ry) / det
        return px to py
    }
}

package com.example.muamaizingbot.vision.roi

import android.graphics.Rect
import com.example.muamaizingbot.vision.coord.RefCoords

object ScaledRoi {

    fun fromRefRect(left: Int, top: Int, right: Int, bottom: Int, frameWidth: Int, frameHeight: Int): Rect {
        return Rect(
            RefCoords.scaleX(left, frameWidth),
            RefCoords.scaleY(top, frameHeight),
            RefCoords.scaleX(right, frameWidth),
            RefCoords.scaleY(bottom, frameHeight),
        )
    }
}

package com.example.muamaizingbot.vision.roi

import android.graphics.Rect

object ScaledRoi {

    private const val REF_WIDTH = 2560
    private const val REF_HEIGHT = 1440

    fun fromRefRect(left: Int, top: Int, right: Int, bottom: Int, frameWidth: Int, frameHeight: Int): Rect {
        return Rect(
            scaleX(left, frameWidth),
            scaleY(top, frameHeight),
            scaleX(right, frameWidth),
            scaleY(bottom, frameHeight)
        )
    }

    private fun scaleX(value: Int, frameWidth: Int): Int {
        return (value.toLong() * frameWidth / REF_WIDTH).toInt()
    }

    private fun scaleY(value: Int, frameHeight: Int): Int {
        return (value.toLong() * frameHeight / REF_HEIGHT).toInt()
    }
}

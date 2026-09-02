package com.example.muamaizingbot.vision.focus

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FocusPortraitClassifierTest {

    @Test
    fun faceRectAtContractSizeIs32x32() {
        val rect = FocusPortraitClassifier.faceRectPx(1280, 720)
        assertNotNull(rect)
        assertArrayEquals(intArrayOf(532, 26, 32, 32), rect)
    }

    @Test
    fun faceRectScalesWithFrame() {
        val rect = FocusPortraitClassifier.faceRectPx(2560, 1440)
        assertNotNull(rect)
        assertEquals(1064, rect!![0])
        assertEquals(52, rect[1])
        assertEquals(64, rect[2])
        assertEquals(64, rect[3])
    }
}

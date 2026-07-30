package com.example.muamaizingbot.bot.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapEntryActionsTest {

    @Test
    fun allZoneHeadsRequireStrongDeepMatch() {
        assertEquals(0.80f, MapEntryActions.headThresholdFor(deepEnough = true))
        assertFalse(0.648f >= MapEntryActions.headThresholdFor(deepEnough = true))
        assertTrue(0.830f >= MapEntryActions.headThresholdFor(deepEnough = true))
        assertEquals(0.38f, MapEntryActions.headThresholdFor(deepEnough = false))
    }

    @Test
    fun resolvesCollapsedTargetFromTwoOrderedSiblingRows() {
        assertEquals(
            489,
            MapEntryActions.resolveFloorRow(
                targetFloor = 3,
                targetCenterY = 459,
                probes = listOf(
                    MapEntryActions.FloorProbe(floor = 1, score = 0.930f, centerY = 429),
                    MapEntryActions.FloorProbe(floor = 2, score = 0.950f, centerY = 459),
                    MapEntryActions.FloorProbe(floor = 3, score = 0.942f, centerY = 459),
                ),
                screenHeight = 720,
            ),
        )
    }

    @Test
    fun keepsTargetWhenItsRowAlignsWithAnotherFloor() {
        assertEquals(
            352,
            MapEntryActions.resolveFloorRow(
                targetFloor = 3,
                targetCenterY = 352,
                probes = listOf(
                    MapEntryActions.FloorProbe(floor = 1, score = 0.925f, centerY = 291),
                    MapEntryActions.FloorProbe(floor = 2, score = 0.930f, centerY = 352),
                    MapEntryActions.FloorProbe(floor = 3, score = 0.938f, centerY = 352),
                ),
                screenHeight = 720,
            ),
        )
    }

    @Test
    fun refusesGeometryWithoutAValidRowSpacing() {
        assertEquals(
            null,
            MapEntryActions.resolveFloorRow(
                targetFloor = 3,
                targetCenterY = 400,
                probes = listOf(
                    MapEntryActions.FloorProbe(floor = 1, score = 0.91f, centerY = 390),
                    MapEntryActions.FloorProbe(floor = 2, score = 0.92f, centerY = 400),
                    MapEntryActions.FloorProbe(floor = 3, score = 0.90f, centerY = 400),
                ),
                screenHeight = 720,
            ),
        )
    }
}

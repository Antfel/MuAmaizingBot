package com.example.muamaizingbot.maps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapDefinitionFullyConfiguredTest {

    @Test
    fun requiresMaintenanceNavigationAndAffine() {
        val incomplete = baseMap(
            maintenance = MapMaintenance("templates/mu/maps/X/m.png"),
            navigation = MapNavigation(
                behavior = "direct_teleport",
                mapOptionTemplate = "templates/mu/maps/X/option.png",
            ),
            mapping = null,
        )
        assertFalse(incomplete.isFullyConfigured())

        val complete = incomplete.copy(
            coordinateMapping = AffineTransform(
                coordX = listOf(0.1, 0.0, 1.0),
                coordY = listOf(0.0, -0.1, 2.0),
            ),
        )
        assertTrue(complete.isFullyConfigured())
    }

    @Test
    fun rejectsBlankOptionTemplate() {
        val map = baseMap(
            maintenance = MapMaintenance("templates/mu/maps/X/m.png"),
            navigation = MapNavigation(
                behavior = "direct_teleport",
                mapOptionTemplate = "",
            ),
            mapping = AffineTransform(
                coordX = listOf(1.0, 0.0, 0.0),
                coordY = listOf(0.0, 1.0, 0.0),
            ),
        )
        assertFalse(map.isFullyConfigured())
    }

    private fun baseMap(
        maintenance: MapMaintenance?,
        navigation: MapNavigation?,
        mapping: AffineTransform?,
    ): MapDefinition {
        return MapDefinition(
            id = "test_map",
            name = "Test Map",
            group = "Test",
            order = 1,
            maintenance = maintenance,
            coordinateMapping = mapping,
            navigation = navigation,
        )
    }
}

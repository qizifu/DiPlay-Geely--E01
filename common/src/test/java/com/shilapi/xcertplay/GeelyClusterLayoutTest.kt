package com.shilapi.xcertplay

import com.shilapi.xcertplay.airplay.CarPlayClusterDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Geely E01 cluster is a plain 1920x720 second display: the stream fills the panel and, unlike
 * the BYD profile, carries no marker safe area, so the phone's own cluster map is not cropped.
 */
class GeelyClusterLayoutTest {
    @Test
    fun streamConfigFillsThePanelWithoutSafeArea() {
        val config = GeelyClusterLayout.streamConfig()
        assertEquals(GeelyClusterLayout.PANEL_WIDTH, config.widthPixels)
        assertEquals(GeelyClusterLayout.PANEL_HEIGHT, config.heightPixels)
        assertNull(config.safeArea)
        assertEquals(true, config.safeAreaDrawOutside)
    }

    @Test
    fun streamConfigAsksForTheClusterMap() {
        val config = GeelyClusterLayout.streamConfig()
        assertEquals(CarPlayClusterDisplay.MAP_URL, config.initialUrl)
    }

    @Test
    fun panelIsTheMeasuredHdmiClusterSize() {
        assertEquals(1920, GeelyClusterLayout.PANEL_WIDTH)
        assertEquals(720, GeelyClusterLayout.PANEL_HEIGHT)
    }
}

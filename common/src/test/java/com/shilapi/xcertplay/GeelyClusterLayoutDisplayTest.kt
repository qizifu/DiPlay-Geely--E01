package com.shilapi.xcertplay

import android.hardware.display.DisplayManager
import android.view.Display
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDisplayManager
import org.robolectric.annotation.Config

/**
 * The Geely cluster is found by its own panel name; an unrelated presentation display (a passenger
 * screen) must never be taken for the cluster.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class GeelyClusterLayoutDisplayTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val manager get() = context.getSystemService(DisplayManager::class.java)

    private fun display(name: String): Int {
        // Display.TYPE_VIRTUAL (5) is hidden from the public Android SDK.
        val id = ShadowDisplayManager.addDisplay("w960dp-h360dp", 5)
        shadowOf(manager.getDisplay(id)).apply {
            setName(name)
            setFlags(Display.FLAG_PRESENTATION)
        }
        return id
    }

    @Test
    fun findsTheGeelyClusterPanelByName() {
        val cluster = display(GeelyClusterLayout.DISPLAY_NAME)
        try {
            assertEquals(cluster, GeelyClusterLayout.findDisplay(context)?.displayId)
        } finally {
            ShadowDisplayManager.removeDisplay(cluster)
        }
    }

    @Test
    fun ignoresAnUnrelatedPresentationDisplay() {
        val other = display("Passenger display")
        try {
            assertNull(GeelyClusterLayout.findDisplay(context))
        } finally {
            ShadowDisplayManager.removeDisplay(other)
        }
    }
}

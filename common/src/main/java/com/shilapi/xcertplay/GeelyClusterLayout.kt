package com.shilapi.xcertplay

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import com.shilapi.xcertplay.airplay.CarPlayClusterDisplay

/**
 * The Geely E01 (GKUI 19) instrument cluster, driven as a plain second Android display.
 *
 * This head unit exposes the cluster as an HDMI presentation display (`displayId=1`, named
 * "HDMI 屏幕", `FLAG_PRESENTATION`). The stock navigation — and a patched public Amap — draw there
 * by creating an ordinary `SurfaceView` on that display, so no vendor service, system signature or
 * AIDL reversal is involved: DiPlay reaches it exactly the same way. Unlike the BYD (DiLink 5)
 * profile there is no theme/layer geometry and no marker safe area — the whole panel shows the
 * stream, which is what CarPlay's own cluster stream already is.
 */
internal object GeelyClusterLayout {
    /** The panel name this firmware reports for the cluster display. */
    const val DISPLAY_NAME = "HDMI 屏幕"

    const val PANEL_WIDTH = 1920
    const val PANEL_HEIGHT = 720

    /** Reported panel width; a 12.3" ~8:3 cluster, the height follows the aspect. */
    private const val WIDTH_PHYSICAL_MM = 292

    /**
     * The cluster stream the iPhone is asked to send (AirPlay stream type 111).
     *
     * Full panel, no BYD marker safe area: the phone's cluster map keeps the car position itself,
     * so the panel needs no reserved centre window here.
     */
    fun streamConfig(): AirPlayDisplayConfig = CarPlayClusterDisplay.config(
        PANEL_WIDTH,
        PANEL_HEIGHT,
        scalePercent = 100,
    ).copy(
        widthPhysicalMm = WIDTH_PHYSICAL_MM,
        safeArea = null,
        safeAreaDrawOutside = true,
    )

    /**
     * The cluster presentation display, or null when this head unit has none.
     *
     * Matches the firmware's own panel name; a name that merely mentions HDMI is accepted as a
     * tolerant second choice. An unrelated presentation display (a passenger screen) is not the
     * cluster and must never be hijacked.
     */
    fun findDisplay(context: Context): Display? {
        val displays = (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
            ?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION) ?: return null
        if (displays.isEmpty()) return null
        return displays.firstOrNull { it.name.trim() == DISPLAY_NAME }
            ?: displays.firstOrNull { it.name.contains("HDMI", ignoreCase = true) }
    }
}

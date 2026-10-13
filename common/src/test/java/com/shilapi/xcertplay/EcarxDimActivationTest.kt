package com.shilapi.xcertplay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The vendor ECARX classes are absent on the JVM test classpath (as they are on any non-privileged
 * install), so activation must fail with a diagnostic instead of throwing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class EcarxDimActivationTest {
    @Test
    fun activationFailsGracefullyAndExplainsWhy() {
        val logs = mutableListOf<String>()
        val activated = EcarxDimActivation.activate(RuntimeEnvironment.getApplication()) { logs.add(it) }
        assertFalse("must not claim success without the vendor API", activated)
        assertTrue("must log the failing stage", logs.any { it.contains("EcarxDim:") })
        assertFalse("no API object may be cached on failure", EcarxDimActivation.available)
    }
}
